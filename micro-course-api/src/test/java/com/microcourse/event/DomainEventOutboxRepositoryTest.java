package com.microcourse.event;

import com.baomidou.mybatisplus.test.autoconfigure.MybatisPlusTest;
import com.microcourse.event.repository.DomainEventOutboxRepository;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V313 domain_event_outbox Repository 测试.
 * P1 plan Task 1.
 */
@MybatisPlusTest
class DomainEventOutboxRepositoryTest {

    @Resource
    DomainEventOutboxRepository repo;

    @Test
    void insert_and_selectByEventId_works() {
        String eventId = UUID.randomUUID().toString();
        DomainEventOutbox row = new DomainEventOutbox();
        row.setEventId(eventId);
        row.setAggregateType("COURSE");
        row.setAggregateId(100L);
        row.setEventType("CREATED");
        row.setPayload("{\"id\":100}");
        row.setTraceId(UUID.randomUUID().toString());
        row.setStatus(OutboxStatus.PENDING);
        row.setAttemptCount(0);
        row.setNextAttemptAt(LocalDateTime.now());
        row.setOccurredAt(LocalDateTime.now());

        repo.insert(row);

        DomainEventOutbox found = repo.selectById(eventId);
        assertNotNull(found);
        assertEquals("COURSE", found.getAggregateType());
        assertEquals(0, found.getAttemptCount());
        assertEquals(OutboxStatus.PENDING, found.getStatus());
    }

    @Test
    void listPendingDueNow_returns_only_pending_with_past_due() {
        // 1. pending + 已到期 → 应返回
        DomainEventOutbox pending = new DomainEventOutbox();
        pending.setEventId(UUID.randomUUID().toString());
        pending.setAggregateType("LESSON");
        pending.setAggregateId(1L);
        pending.setEventType("UPDATED");
        pending.setPayload("{}");
        pending.setTraceId(UUID.randomUUID().toString());
        pending.setStatus(OutboxStatus.PENDING);
        pending.setAttemptCount(0);
        pending.setNextAttemptAt(LocalDateTime.now().minusMinutes(1));
        pending.setOccurredAt(LocalDateTime.now().minusMinutes(1));
        repo.insert(pending);

        // 2. pending + 未到期 → 不应返回
        DomainEventOutbox future = new DomainEventOutbox();
        future.setEventId(UUID.randomUUID().toString());
        future.setAggregateType("LESSON");
        future.setAggregateId(2L);
        future.setEventType("UPDATED");
        future.setPayload("{}");
        future.setTraceId(UUID.randomUUID().toString());
        future.setStatus(OutboxStatus.PENDING);
        future.setAttemptCount(0);
        future.setNextAttemptAt(LocalDateTime.now().plusMinutes(10));
        future.setOccurredAt(LocalDateTime.now());
        repo.insert(future);

        // 3. delivered → 不应返回
        DomainEventOutbox delivered = new DomainEventOutbox();
        delivered.setEventId(UUID.randomUUID().toString());
        delivered.setAggregateType("LESSON");
        delivered.setAggregateId(3L);
        delivered.setEventType("UPDATED");
        delivered.setPayload("{}");
        delivered.setTraceId(UUID.randomUUID().toString());
        delivered.setStatus(OutboxStatus.DELIVERED);
        delivered.setAttemptCount(1);
        delivered.setNextAttemptAt(LocalDateTime.now().minusMinutes(1));
        delivered.setOccurredAt(LocalDateTime.now().minusMinutes(1));
        delivered.setDeliveredAt(LocalDateTime.now());
        repo.insert(delivered);

        List<DomainEventOutbox> rows = repo.listPendingDueNow(100);
        assertTrue(rows.stream().anyMatch(r -> r.getEventId().equals(pending.getEventId())),
            "PENDING + past due 应被返回");
        assertFalse(rows.stream().anyMatch(r -> r.getEventId().equals(future.getEventId())),
            "PENDING + future 不应返回");
        assertFalse(rows.stream().anyMatch(r -> r.getEventId().equals(delivered.getEventId())),
            "DELIVERED 不应返回");
    }

    @Test
    void markDelivered_transitions_to_DELIVERED() {
        String eventId = UUID.randomUUID().toString();
        DomainEventOutbox row = new DomainEventOutbox();
        row.setEventId(eventId);
        row.setAggregateType("COURSE");
        row.setAggregateId(200L);
        row.setEventType("UPDATED");
        row.setPayload("{}");
        row.setTraceId(UUID.randomUUID().toString());
        row.setStatus(OutboxStatus.PENDING);
        row.setAttemptCount(0);
        row.setNextAttemptAt(LocalDateTime.now());
        row.setOccurredAt(LocalDateTime.now());
        repo.insert(row);

        LocalDateTime now = LocalDateTime.now();
        assertEquals(1, repo.markDelivered(eventId, now, now));

        DomainEventOutbox reloaded = repo.selectById(eventId);
        assertEquals(OutboxStatus.DELIVERED, reloaded.getStatus());
        assertNotNull(reloaded.getDeliveredAt());
    }

    @Test
    void markRetry_increments_attempt_count() {
        String eventId = UUID.randomUUID().toString();
        DomainEventOutbox row = new DomainEventOutbox();
        row.setEventId(eventId);
        row.setAggregateType("LESSON");
        row.setAggregateId(300L);
        row.setEventType("UPDATED");
        row.setPayload("{}");
        row.setTraceId(UUID.randomUUID().toString());
        row.setStatus(OutboxStatus.PENDING);
        row.setAttemptCount(0);
        row.setNextAttemptAt(LocalDateTime.now());
        row.setOccurredAt(LocalDateTime.now());
        repo.insert(row);

        LocalDateTime next = LocalDateTime.now().plusMinutes(30);
        assertEquals(1, repo.markRetry(eventId, next, "500 Internal Server Error", LocalDateTime.now()));

        DomainEventOutbox reloaded = repo.selectById(eventId);
        assertEquals(OutboxStatus.PENDING, reloaded.getStatus());
        assertEquals(1, reloaded.getAttemptCount());
        assertEquals("500 Internal Server Error", reloaded.getLastError());
    }

    @Test
    void markDeadLetter_transitions_to_DEAD_LETTER() {
        String eventId = UUID.randomUUID().toString();
        DomainEventOutbox row = new DomainEventOutbox();
        row.setEventId(eventId);
        row.setAggregateType("CHAPTER");
        row.setAggregateId(400L);
        row.setEventType("DELETED");
        row.setPayload("{}");
        row.setTraceId(UUID.randomUUID().toString());
        row.setStatus(OutboxStatus.PENDING);
        row.setAttemptCount(5);
        row.setNextAttemptAt(LocalDateTime.now().minusMinutes(1));
        row.setOccurredAt(LocalDateTime.now());
        repo.insert(row);

        assertEquals(1, repo.markDeadLetter(eventId, LocalDateTime.now()));

        DomainEventOutbox reloaded = repo.selectById(eventId);
        assertEquals(OutboxStatus.DEAD_LETTER, reloaded.getStatus());
    }
}
