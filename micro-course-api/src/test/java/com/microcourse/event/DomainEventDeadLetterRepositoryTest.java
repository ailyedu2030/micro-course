package com.microcourse.event;

import com.microcourse.event.repository.DomainEventDeadLetterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * DomainEventDeadLetterRepository 测试 (mock 模式).
 */
class DomainEventDeadLetterRepositoryTest {

    private DomainEventDeadLetterRepository repo;

    @BeforeEach
    void setup() {
        repo = mock(DomainEventDeadLetterRepository.class);
        when(repo.insert(any(DomainEventDeadLetter.class))).thenReturn(1);
        when(repo.updateById(any(DomainEventDeadLetter.class))).thenReturn(1);
    }

    @Test
    void save_persists_aggregate_type_and_payload() {
        String eventId = UUID.randomUUID().toString();
        DomainEventDeadLetter row = new DomainEventDeadLetter();
        row.setEventId(eventId);
        row.setAggregateType("LESSON");
        row.setAggregateId(99L);
        row.setPayload("{\"id\":99}");
        row.setLastError("timeout after 5 attempts");

        repo.insert(row);

        ArgumentCaptor<DomainEventDeadLetter> captor = ArgumentCaptor.forClass(DomainEventDeadLetter.class);
        verify(repo).insert(captor.capture());
        assertEquals(eventId, captor.getValue().getEventId());
        assertEquals("timeout after 5 attempts", captor.getValue().getLastError());
    }

    @Test
    void ack_updates_operator_and_acknowledged_at() {
        String eventId = UUID.randomUUID().toString();
        DomainEventDeadLetter row = new DomainEventDeadLetter();
        row.setEventId(eventId);
        row.setAggregateType("CHAPTER");
        row.setPayload("{}");
        row.setOperator("ops-alice");

        repo.updateById(row);

        ArgumentCaptor<DomainEventDeadLetter> captor = ArgumentCaptor.forClass(DomainEventDeadLetter.class);
        verify(repo).updateById(captor.capture());
        assertEquals("ops-alice", captor.getValue().getOperator());
    }

    @Test
    void multiple_dead_letters_kept_independent() {
        DomainEventDeadLetter a = new DomainEventDeadLetter();
        a.setEventId(UUID.randomUUID().toString());
        a.setAggregateType("COURSE");
        a.setLastError("500");

        DomainEventDeadLetter b = new DomainEventDeadLetter();
        b.setEventId(UUID.randomUUID().toString());
        b.setAggregateType("LESSON");
        b.setLastError("503");

        repo.insert(a);
        repo.insert(b);

        verify(repo, times(2)).insert(any(DomainEventDeadLetter.class));
    }
}
