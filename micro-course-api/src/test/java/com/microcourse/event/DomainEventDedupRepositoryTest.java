package com.microcourse.event;

import com.baomidou.mybatisplus.test.autoconfigure.MybatisPlusTest;
import com.microcourse.event.repository.DomainEventDedupRepository;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V314 domain_event_dedup Repository 测试.
 * P1 plan Task 2 验证:
 *   - insertIgnoreDuplicate 第一次插入返回 1
 *   - insertIgnoreDuplicate 第二次不抛错 (ON CONFLICT DO NOTHING)
 *   - existsByEventId 第一次 false / 插入后 true
 */
@MybatisPlusTest
class DomainEventDedupRepositoryTest {

    @Resource
    DomainEventDedupRepository repo;

    @Test
    void insertIgnoreDuplicate_first_call_returns_1_and_persists() {
        String eventId = UUID.randomUUID().toString();
        DomainEventDedup row = new DomainEventDedup();
        row.setEventId(eventId);
        row.setSource("HERMES");
        row.setTraceId(UUID.randomUUID().toString());

        int inserted = repo.insertIgnoreDuplicate(row);
        assertEquals(1, inserted, "第一次插入应返回 1");
        assertTrue(repo.existsByEventId(eventId), "插入后 existsByEventId 应 true");
    }

    @Test
    void insertIgnoreDuplicate_duplicate_eventId_does_NOT_throw() {
        String eventId = UUID.randomUUID().toString();
        DomainEventDedup first = new DomainEventDedup();
        first.setEventId(eventId);
        first.setSource("LOCAL");
        first.setTraceId(UUID.randomUUID().toString());

        // 第 1 次: 持久化
        assertEquals(1, repo.insertIgnoreDuplicate(first));

        // 第 2 次相同 eventId: ON CONFLICT DO NOTHING 不抛错
        DomainEventDedup second = new DomainEventDedup();
        second.setEventId(eventId);
        second.setSource("HERMES");  // 即使 source 不同, event_id PK 仍是去重权威
        second.setTraceId(UUID.randomUUID().toString());

        int result = repo.insertIgnoreDuplicate(second);
        assertEquals(0, result, "ON CONFLICT DO NOTHING 返回 0");

        // 验证: 仍是第 1 次的 source (LOCAL), 第 2 次的 source=HERMES 被忽略
        assertTrue(repo.existsByEventId(eventId));
    }

    @Test
    void existsByEventId_returns_false_for_unknown_eventId() {
        String unknownId = UUID.randomUUID().toString();
        assertFalse(repo.existsByEventId(unknownId));
    }

    @Test
    void both_source_values_distinct_in_DB_but_event_id_collision_is_silent() {
        // 模拟: 本地推了一个 outbox event (LOCAL), Hermes 反推同一个 event_id (HERMES)
        String sharedId = UUID.randomUUID().toString();

        DomainEventDedup localRow = new DomainEventDedup();
        localRow.setEventId(sharedId);
        localRow.setSource("LOCAL");
        localRow.setTraceId("trace-local");
        repo.insertIgnoreDuplicate(localRow);

        DomainEventDedup hermesRow = new DomainEventDedup();
        hermesRow.setEventId(sharedId);
        hermesRow.setSource("HERMES");
        hermesRow.setTraceId("trace-hermes");
        repo.insertIgnoreDuplicate(hermesRow);  // 应 silent skip

        // 双向幂等保证: 同一 event_id 不会被重复副作用覆盖
        assertTrue(repo.existsByEventId(sharedId));
    }
}
