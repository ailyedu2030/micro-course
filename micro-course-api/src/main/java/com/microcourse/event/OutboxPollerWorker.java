package com.microcourse.event;

import com.microcourse.event.repository.DomainEventOutboxRepository;
import com.microcourse.event.repository.DomainEventDeadLetterRepository;
import com.microcourse.event.repository.DomainEventDedupRepository;
import com.microcourse.event.HermesEventPushClient.PushException;
import com.microcourse.event.HermesEventPushClient.PushResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * P1 plan Task 11: outbox poller 定时拉取 PENDING 行, 推 Hermes, 处理 retry/dead_letter.
 *
 * 节奏: ${hermes.outbox.poll-interval-ms:5000} (默认 5s, 测试可缩到 200ms).
 *
 * 状态流转:
 *   PENDING + 202/409 → DELIVERED
 *   PENDING + 5xx/网络 → markRetry (按 RetryPolicy 退避) + 重试
 *   PENDING + 5 次均失败 → 移 dead_letter + markDeadLetter
 *
 * 同时 P1 plan Task 14 暴露 Prometheus 指标 (microcourse_outbox_pending_total / _retry_total / _delivered_total / _dead_letter_total):
 *   - Spring Boot Actuator 暴露 /actuator/prometheus
 *   - 监控告警 (monitoring/prometheus/alerts.yml) 用这些 metric 评估
 */
@Component
public class OutboxPollerWorker {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxPollerWorker.class);

    private final DomainEventOutboxRepository outboxRepo;
    private final DomainEventDeadLetterRepository dlqRepo;
    private final DomainEventDedupRepository dedupRepo;
    private final HermesEventPushClient pushClient;
    private final MeterRegistry meterRegistry;

    private final AtomicLong pendingGauge = new AtomicLong(0);
    private final AtomicLong retryGauge = new AtomicLong(0);
    private final AtomicLong unackedDeadLetterGauge = new AtomicLong(0);
    private Counter deliveredCounter;
    private Counter deadLetterCounter;
    private Counter retryCounter;

    @Value("${hermes.outbox.batch-size:20}")
    private int batchSize;

    public OutboxPollerWorker(DomainEventOutboxRepository outboxRepo,
                              DomainEventDeadLetterRepository dlqRepo,
                              DomainEventDedupRepository dedupRepo,
                              HermesEventPushClient pushClient,
                              MeterRegistry meterRegistry) {
        this.outboxRepo = outboxRepo;
        this.dlqRepo = dlqRepo;
        this.dedupRepo = dedupRepo;
        this.pushClient = pushClient;
        this.meterRegistry = meterRegistry;
        registerMetrics();
    }

    private void registerMetrics() {
        meterRegistry.gauge("microcourse_outbox_pending_total", pendingGauge);
        meterRegistry.gauge("microcourse_outbox_retry_total", retryGauge);
        meterRegistry.gauge("microcourse_dead_letter_unacked", unackedDeadLetterGauge);
        deliveredCounter = Counter.builder("microcourse_outbox_delivered_total")
                .description("累计成功 DELIVERED 行数")
                .register(meterRegistry);
        deadLetterCounter = Counter.builder("microcourse_dead_letter_total")
                .description("累计移入 dead_letter 行数")
                .register(meterRegistry);
        retryCounter = Counter.builder("microcourse_outbox_retry_attempts_total")
                .description("累计 markRetry 次数")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${hermes.outbox.poll-interval-ms:5000}")
    public void pollOnce() {
        LocalDateTime now = LocalDateTime.now();
        List<DomainEventOutbox> rows = outboxRepo.listPendingDueNow(now, batchSize);
        // 更新 gauge: 反映当前 PENDING / RETRY 状态
        long pending = rows.size();
        long retry = rows.stream().filter(r -> r.getAttemptCount() != null && r.getAttemptCount() > 0).count();
        pendingGauge.set(pending);
        retryGauge.set(retry);

        // 死信表未 ack 行数
        long unacked;
        try {
            Long count = dlqRepo.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DomainEventDeadLetter>()
                            .isNull("acknowledged_at"));
            unacked = count == null ? 0L : count;
        } catch (Exception e) {
            unacked = 0L;
        }
        unackedDeadLetterGauge.set(unacked);

        if (rows.isEmpty()) return;
        LOG.info("[Outbox] polling {} pending events", rows.size());
        for (DomainEventOutbox row : rows) {
            try {
                handleOne(row);
            } catch (Exception e) {
                LOG.error("[Outbox] handler error eventId={}", row.getEventId(), e);
            }
        }
    }

    @Transactional
    protected void handleOne(DomainEventOutbox row) {
        DomainEvent event = DomainEvent.fromJsonPayload(row.getPayload());

        if (dedupRepo.existsByEventId(event.getEventId())) {
            LOG.info("[Outbox] dedup hit, marking as delivered, eventId={}", event.getEventId());
            outboxRepo.markDelivered(event.getEventId(), LocalDateTime.now(), LocalDateTime.now());
            deliveredCounter.increment();
            return;
        }

        try {
            PushResult result = pushClient.push(event);
            if (result.accepted() || result.statusCode() == 409) {
                outboxRepo.markDelivered(event.getEventId(), LocalDateTime.now(), LocalDateTime.now());
                deliveredCounter.increment();
                LOG.info("[Outbox] DELIVERED eventId={} status={}", event.getEventId(), result.statusCode());
            } else {
                handleFailure(row, "4xx " + result.statusCode() + " " + result.body());
            }
        } catch (PushException e) {
            handleFailure(row, e.getMessage());
        }
    }

    private void handleFailure(DomainEventOutbox row, String errMsg) {
        int nextAttempt = (row.getAttemptCount() == null ? 0 : row.getAttemptCount()) + 1;
        if (RetryPolicy.shouldDeadLetter(nextAttempt)) {
            DomainEventDeadLetter dlq = new DomainEventDeadLetter();
            dlq.setEventId(row.getEventId());
            dlq.setAggregateType(row.getAggregateType());
            dlq.setAggregateId(row.getAggregateId());
            dlq.setPayload(row.getPayload());
            dlq.setLastError(errMsg);
            dlqRepo.insert(dlq);
            outboxRepo.markDeadLetter(row.getEventId(), LocalDateTime.now());
            deadLetterCounter.increment();
            LOG.error("[Outbox] DEAD_LETTER eventId={} after {} attempts, err={}",
                    row.getEventId(), nextAttempt, errMsg);
        } else {
            outboxRepo.markRetry(row.getEventId(),
                    RetryPolicy.nextAttemptAt(nextAttempt),
                    errMsg,
                    LocalDateTime.now());
            retryCounter.increment();
            LOG.warn("[Outbox] RETRY eventId={} attempt={} nextAt={} err={}",
                    row.getEventId(), nextAttempt,
                    RetryPolicy.nextAttemptAt(nextAttempt), errMsg);
        }
    }
}
