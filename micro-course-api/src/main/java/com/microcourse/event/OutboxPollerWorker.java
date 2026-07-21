package com.microcourse.event;

import com.microcourse.event.repository.DomainEventOutboxRepository;
import com.microcourse.event.repository.DomainEventDeadLetterRepository;
import com.microcourse.event.repository.DomainEventDedupRepository;
import com.microcourse.event.HermesEventPushClient.PushException;
import com.microcourse.event.HermesEventPushClient.PushResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * P1 plan Task 11: outbox poller 定时拉取 PENDING 行, 推 Hermes, 处理 retry/dead_letter.
 *
 * 节奏: ${hermes.outbox.poll-interval-ms:5000} (默认 5s, 测试可缩到 200ms).
 *
 * 状态流转:
 *   PENDING + 202/409 → DELIVERED
 *   PENDING + 5xx/网络 → markRetry (按 RetryPolicy 退避) + 重试
 *   PENDING + 5 次均失败 → 移 dead_letter + markDeadLetter
 */
@Component
public class OutboxPollerWorker {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxPollerWorker.class);

    private final DomainEventOutboxRepository outboxRepo;
    private final DomainEventDeadLetterRepository dlqRepo;
    private final DomainEventDedupRepository dedupRepo;
    private final HermesEventPushClient pushClient;

    @Value("${hermes.outbox.batch-size:20}")
    private int batchSize;

    public OutboxPollerWorker(DomainEventOutboxRepository outboxRepo,
                              DomainEventDeadLetterRepository dlqRepo,
                              DomainEventDedupRepository dedupRepo,
                              HermesEventPushClient pushClient) {
        this.outboxRepo = outboxRepo;
        this.dlqRepo = dlqRepo;
        this.dedupRepo = dedupRepo;
        this.pushClient = pushClient;
    }

    @Scheduled(fixedDelayString = "${hermes.outbox.poll-interval-ms:5000}")
    public void pollOnce() {
        List<DomainEventOutbox> rows = outboxRepo.listPendingDueNow(batchSize);
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
        // 1. 反序列化为 DomainEvent
        DomainEvent event = DomainEvent.fromJsonPayload(row.getPayload());

        // 2. 防御性 dedup: 本地 outbound 时一般用不到, 但保留检测, 防意外
        if (dedupRepo.existsByEventId(event.getEventId())) {
            LOG.info("[Outbox] dedup hit, marking as delivered, eventId={}", event.getEventId());
            outboxRepo.markDelivered(event.getEventId(), LocalDateTime.now(), LocalDateTime.now());
            return;
        }

        // 3. 推 Hermes
        try {
            PushResult result = pushClient.push(event);
            if (result.accepted() || result.statusCode() == 409) {
                outboxRepo.markDelivered(event.getEventId(), LocalDateTime.now(), LocalDateTime.now());
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
            LOG.error("[Outbox] DEAD_LETTER eventId={} after {} attempts, err={}",
                    row.getEventId(), nextAttempt, errMsg);
        } else {
            outboxRepo.markRetry(row.getEventId(),
                    RetryPolicy.nextAttemptAt(nextAttempt),
                    errMsg,
                    LocalDateTime.now());
            LOG.warn("[Outbox] RETRY eventId={} attempt={} nextAt={} err={}",
                    row.getEventId(), nextAttempt,
                    RetryPolicy.nextAttemptAt(nextAttempt), errMsg);
        }
    }
}
