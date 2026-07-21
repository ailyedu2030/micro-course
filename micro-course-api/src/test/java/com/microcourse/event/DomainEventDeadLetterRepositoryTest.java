package com.microcourse.event;

import com.baomidou.mybatisplus.test.autoconfigure.MybatisPlusTest;
import com.microcourse.event.repository.DomainEventDeadLetterRepository;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V315 domain_event_dead_letter Repository 测试.
 * P1 plan Task 3.
 */
@MybatisPlusTest
class DomainEventDeadLetterRepositoryTest {

    @Resource
    DomainEventDeadLetterRepository repo;

    @Test
    void saveAndFindById() {
        String eventId = UUID.randomUUID().toString();
        DomainEventDeadLetter row = new DomainEventDeadLetter();
        row.setEventId(eventId);
        row.setAggregateType("LESSON");
        row.setAggregateId(99L);
        row.setPayload("{\"id\":99}");
        row.setLastError("timeout after 5 attempts");

        repo.insert(row);

        DomainEventDeadLetter found = repo.selectById(eventId);
        assertNotNull(found);
        assertEquals("timeout after 5 attempts", found.getLastError());
        assertNull(found.getAcknowledgedAt());
        assertNull(found.getOperator());
    }

    @Test
    void ack_sets_operator_and_acknowledged_at() {
        String eventId = UUID.randomUUID().toString();
        DomainEventDeadLetter row = new DomainEventDeadLetter();
        row.setEventId(eventId);
        row.setAggregateType("CHAPTER");
        row.setAggregateId(100L);
        row.setPayload("{}");
        row.setLastError("404 not found");
        repo.insert(row);

        // 模拟人工 ack
        row.setOperator("ops-alice");
        row.setAcknowledgedAt(java.time.LocalDateTime.now());
        repo.updateById(row);

        DomainEventDeadLetter reloaded = repo.selectById(eventId);
        assertEquals("ops-alice", reloaded.getOperator());
        assertNotNull(reloaded.getAcknowledgedAt());
    }

    @Test
    void multiple_dead_letters_are_independent() {
        String evtA = UUID.randomUUID().toString();
        String evtB = UUID.randomUUID().toString();

        DomainEventDeadLetter a = new DomainEventDeadLetter();
        a.setEventId(evtA);
        a.setAggregateType("COURSE");
        a.setPayload("{}");
        a.setLastError("500");
        repo.insert(a);

        DomainEventDeadLetter b = new DomainEventDeadLetter();
        b.setEventId(evtB);
        b.setAggregateType("LESSON");
        b.setPayload("{}");
        b.setLastError("503");
        repo.insert(b);

        DomainEventDeadLetter reloadedA = repo.selectById(evtA);
        DomainEventDeadLetter reloadedB = repo.selectById(evtB);

        assertEquals("500", reloadedA.getLastError());
        assertEquals("503", reloadedB.getLastError());
        assertEquals("COURSE", reloadedA.getAggregateType());
        assertEquals("LESSON", reloadedB.getAggregateType());
    }
}
