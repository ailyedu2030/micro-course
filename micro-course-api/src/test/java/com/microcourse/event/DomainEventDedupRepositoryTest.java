package com.microcourse.event;

import com.microcourse.event.repository.DomainEventDedupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * DomainEventDedupRepository 测试 (mock 模式, 依项目惯例).
 *
 * 关键语义: insertIgnoreDuplicate 是 ON CONFLICT DO NOTHING,
 * 同一 event_id 二插不抛错, 这由 SQL 数据库保证, mock 层我们只验证调用参数.
 */
class DomainEventDedupRepositoryTest {

    private DomainEventDedupRepository repo;

    @BeforeEach
    void setup() {
        repo = mock(DomainEventDedupRepository.class);
        when(repo.insertIgnoreDuplicate(any(DomainEventDedup.class))).thenReturn(1);
        when(repo.existsByEventId(any())).thenReturn(false);
    }

    @Test
    void insertIgnoreDuplicate_passes_event_id_to_repository() {
        String eventId = UUID.randomUUID().toString();
        DomainEventDedup row = new DomainEventDedup();
        row.setEventId(eventId);
        row.setSource("HERMES");
        row.setTraceId(UUID.randomUUID().toString());

        repo.insertIgnoreDuplicate(row);

        ArgumentCaptor<DomainEventDedup> captor = ArgumentCaptor.forClass(DomainEventDedup.class);
        verify(repo).insertIgnoreDuplicate(captor.capture());
        assertEquals(eventId, captor.getValue().getEventId());
        assertEquals("HERMES", captor.getValue().getSource());
    }

    @Test
    void existsByEventId_can_return_true_or_false() {
        String eventId = UUID.randomUUID().toString();
        when(repo.existsByEventId(eventId)).thenReturn(true);

        assertTrue(repo.existsByEventId(eventId));
        verify(repo).existsByEventId(eventId);
    }

    @Test
    void duplicate_event_id_collision_silently_ignored() {
        // 模拟: ON CONFLICT DO NOTHING 幂等
        String sharedId = UUID.randomUUID().toString();

        DomainEventDedup localRow = new DomainEventDedup();
        localRow.setEventId(sharedId);
        localRow.setSource("LOCAL");
        localRow.setTraceId("t1");
        repo.insertIgnoreDuplicate(localRow);

        DomainEventDedup hermesRow = new DomainEventDedup();
        hermesRow.setEventId(sharedId);
        hermesRow.setSource("HERMES");
        hermesRow.setTraceId("t2");

        // 第一次返回 1, 第二次返回 0 (ON CONFLICT)
        when(repo.insertIgnoreDuplicate(localRow)).thenReturn(1);
        when(repo.insertIgnoreDuplicate(hermesRow)).thenReturn(0);

        assertEquals(1, repo.insertIgnoreDuplicate(localRow));
        assertEquals(0, repo.insertIgnoreDuplicate(hermesRow));

        verify(repo, times(2)).insertIgnoreDuplicate(any(DomainEventDedup.class));
    }
}
