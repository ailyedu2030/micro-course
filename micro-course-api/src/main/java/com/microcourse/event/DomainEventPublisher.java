package com.microcourse.event;

import com.microcourse.event.repository.DomainEventOutboxRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DomainEventPublisher: 同事务 atomic publish.
 * P1 spec §三.3.4 + §五.5.4:
 *   - publish() 必须在 DB 事务内被调用 (与业务 INSERT 同事务落 outbox)
 *   - publishRaw() 仅供 @Transactional repository 方法内部使用
 *   - 业务事务回滚时, outbox 行也回滚, 保证一致性 (no event without state)
 *
 * 这是 P1 关键的"事件 + 业务 = 同一原子"的 invariant 实现.
 */
@Component
public class DomainEventPublisher {

    @Autowired
    private DomainEventOutboxRepository outboxRepo;

    /**
     * 便捷入口: 在当前事务内 atomic publish.
     *
     * 若调用方未在事务内, 抛 IllegalStateException.
     * 这是一个硬约束, 防止"裸 publish" 产生不一致状态:
     *   - 业务 INSERT 已提交但 outbox 没写 → 永远丢失
     *   - outbox 写了但业务 INSERT 未提交 → 推给 Hermes 后再回滚
     *
     * @param eventId    UUID v4 PK + dedup.event_id 共享命名空间
     * @param aggregateType COURSE / CHAPTER / SECTION / LESSON
     * @param aggregateId 对应主键
     * @param eventType   CREATED / UPDATED / DELETED / REORDERED
     * @param payload     已序列化的 JSON 字符串
     */
    public void publish(String eventId, String aggregateType, Long aggregateId,
                        String eventType, String payload) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                "DomainEventPublisher.publish must be called inside an active DB transaction. " +
                "Use publishRaw(...) only inside repositories' @Transactional scope. " +
                "Current thread: " + Thread.currentThread().getName()
            );
        }
        doInsert(eventId, aggregateType, aggregateId, eventType, payload);
    }

    /**
     * 受信任入口: 仅供 @Transactional 方法内部使用 (用于嵌套事件 / 异步场景).
     */
    public void publishRaw(String eventId, String aggregateType, Long aggregateId,
                           String eventType, String payload) {
        doInsert(eventId, aggregateType, aggregateId, eventType, payload);
    }

    private void doInsert(String eventId, String aggregateType, Long aggregateId,
                          String eventType, String payload) {
        DomainEventOutbox row = new DomainEventOutbox();
        row.setEventId(eventId);
        row.setAggregateType(aggregateType);
        row.setAggregateId(aggregateId);
        row.setEventType(eventType);
        row.setPayload(payload);
        row.setTraceId(UUID.randomUUID().toString());
        row.setStatus(OutboxStatus.PENDING);
        row.setAttemptCount(0);
        row.setOccurredAt(LocalDateTime.now());
        row.setNextAttemptAt(LocalDateTime.now());
        outboxRepo.insert(row);
    }
}
