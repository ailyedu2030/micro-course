package com.microcourse.event;

import com.microcourse.event.repository.DomainEventOutboxRepository;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DomainEventPublisher 测试.
 * P1 plan Task 5 验证 2 个 invariant:
 *   - publish() 必须在事务内, 否则 IllegalStateException
 *   - publishRaw() 内部直接走 insert, 适用于 @Transactional 嵌套
 */
@SpringBootTest
class DomainEventPublisherTest {

    @Autowired DomainEventPublisher publisher;
    @Resource DomainEventOutboxRepository outboxRepo;

    @Test
    @Transactional
    void publish_in_same_transaction_then_outbox_row_exists() {
        String eventId = UUID.randomUUID().toString();
        publisher.publish(eventId, "COURSE", 1L, "CREATED", "{\"id\":1}");

        DomainEventOutbox row = outboxRepo.selectById(eventId);
        assertNotNull(row, "事务内 publish 后, outbox 行应已存在");
        assertEquals("COURSE", row.getAggregateType());
        assertEquals(OutboxStatus.PENDING, row.getStatus());
        assertEquals(0, row.getAttemptCount());
        assertNotNull(row.getTraceId());
    }

    @Test
    @Transactional
    void publish_persists_payload_verbatim() {
        String eventId = UUID.randomUUID().toString();
        String payload = "{\"id\":42,\"title\":\"test\"}";
        publisher.publish(eventId, "LESSON", 42L, "UPDATED", payload);

        DomainEventOutbox row = outboxRepo.selectById(eventId);
        assertNotNull(row);
        assertEquals(payload, row.getPayload(), "payload 必须原样持久化");
    }

    @Test
    void publish_outside_transaction_throws_IllegalStateException() {
        // 测试无 @Transactional 的方法直接调用
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            publisher.publish("invalid-event-no-tx", "COURSE", 1L, "CREATED", "{}")
        );
        assertTrue(ex.getMessage().contains("must be called inside an active DB transaction"),
            "错误信息应明确说明 invariant 要求");
    }

    @Test
    @Transactional
    void publishRaw_in_transaction_works() {
        String eventId = UUID.randomUUID().toString();
        // publishRaw 跳过检查, 由调用方自己 @Transactional 兜底
        publisher.publishRaw(eventId, "CHAPTER", 5L, "DELETED", "{\"id\":5}");

        DomainEventOutbox row = outboxRepo.selectById(eventId);
        assertNotNull(row);
        assertEquals("CHAPTER", row.getAggregateType());
        assertEquals("DELETED", row.getEventType());
    }

    @Test
    @Transactional
    void multi_publish_in_same_tx_creates_multiple_outbox_rows() {
        String id1 = UUID.randomUUID().toString();
        String id2 = UUID.randomUUID().toString();

        publisher.publish(id1, "COURSE", 100L, "CREATED", "{}");
        publisher.publish(id2, "LESSON", 200L, "UPDATED", "{}");

        assertNotNull(outboxRepo.selectById(id1));
        assertNotNull(outboxRepo.selectById(id2));
    }
}
