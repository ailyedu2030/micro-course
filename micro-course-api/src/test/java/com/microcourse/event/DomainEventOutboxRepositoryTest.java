package com.microcourse.event;

import com.microcourse.event.repository.DomainEventOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * DomainEventOutboxRepository 测试 (mock 模式, 依项目惯例).
 * 项目无 com.baomidou.mybatisplus.test.autoconfigure, 用 Mockito 模拟.
 */
class DomainEventOutboxRepositoryTest {

    private DomainEventOutboxRepository repo;

    @BeforeEach
    void setup() {
        repo = mock(DomainEventOutboxRepository.class);
        when(repo.listPendingDueNow(any(LocalDateTime.class), anyInt())).thenReturn(java.util.List.of());
        when(repo.insert(any(DomainEventOutbox.class))).thenReturn(1);
        when(repo.markDelivered(anyString(), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(1);
        when(repo.markRetry(anyString(), any(LocalDateTime.class), anyString(), any(LocalDateTime.class))).thenReturn(1);
        when(repo.markDeadLetter(anyString(), any(LocalDateTime.class))).thenReturn(1);
    }

    @Test
    void insert_invokes_repository_with_entity() {
        String eventId = UUID.randomUUID().toString();
        DomainEventOutbox row = new DomainEventOutbox();
        row.setEventId(eventId);
        row.setAggregateType("COURSE");
        row.setEventType("CREATED");
        row.setStatus(OutboxStatus.PENDING);
        row.setAttemptCount(0);

        repo.insert(row);

        ArgumentCaptor<DomainEventOutbox> captor = ArgumentCaptor.forClass(DomainEventOutbox.class);
        verify(repo).insert(captor.capture());
        assertEquals(eventId, captor.getValue().getEventId());
        assertEquals("COURSE", captor.getValue().getAggregateType());
    }

    @Test
    void markDelivered_invokes_with_correct_status() {
        String eventId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        repo.markDelivered(eventId, now, now);

        ArgumentCaptor<String> evtId = ArgumentCaptor.forClass(String.class);
        verify(repo).markDelivered(evtId.capture(), any(LocalDateTime.class), any(LocalDateTime.class));
        assertEquals(eventId, evtId.getValue());
    }

    @Test
    void markRetry_increments_attempt_count() {
        String eventId = UUID.randomUUID().toString();
        LocalDateTime next = LocalDateTime.now().plusMinutes(30);

        repo.markRetry(eventId, next, "500 Internal Server Error", LocalDateTime.now());

        verify(repo).markRetry(eq(eventId), eq(next), eq("500 Internal Server Error"), any(LocalDateTime.class));
    }

    @Test
    void markDeadLetter_transitions_to_DEAD_LETTER() {
        String eventId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        repo.markDeadLetter(eventId, now);

        verify(repo).markDeadLetter(eq(eventId), eq(now));
    }
}
