package com.microcourse.event;

import com.microcourse.event.dto.LessonEventPayload;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/**
 * DomainEvent 序列化/反序列化测试.
 * P1 plan Task 4 验证:
 *   - toJsonPayload / fromJsonPayload 往返一致
 *   - LocalDateTime 正确处理 (不变成 timestamp)
 *   - getPayloadAs<T> 嵌套对象转换
 *
 * 用手写 fluent setter (项目禁用 Lombok 注解处理器).
 */
class DomainEventTest {

    @Test
    void toJsonPayload_and_fromJsonPayload_roundTrip() {
        LessonEventPayload payload = new LessonEventPayload()
                .setId(12345L)
                .setTitle("Lesson 1")
                .setLessonType("PPT")
                .setContentUrl("https://cdn.example.com/lesson/12345.html")
                .setSortOrder(1);

        DomainEvent original = new DomainEvent()
                .setEventId(UUID.randomUUID().toString())
                .setTraceId(UUID.randomUUID().toString())
                .setOccurredAt(java.time.LocalDateTime.of(2026, 7, 21, 10, 30, 0))
                .setAggregateType("LESSON")
                .setAggregateId(12345L)
                .setEventType("UPDATED")
                .setAction("HTTP_POST")
                .setEndpoint("/api/hermes/webhook/courses/HER-X/lessons")
                .setHermesCourseId("HER-X")
                .setPayload(payload);

        String json = original.toJsonPayload();
        assertNotNull(json);
        assertTrue(json.contains("12345"));
        assertTrue(json.contains("PPT"));
        // LocalDateTime 不应变成 timestamp 数字
        assertTrue(json.contains("2026"), "LocalDateTime 应保留 ISO 字符串: " + json);

        DomainEvent restored = DomainEvent.fromJsonPayload(json);
        assertEquals(original.getEventId(), restored.getEventId());
        assertEquals(original.getTraceId(), restored.getTraceId());
        assertEquals(original.getAggregateId(), restored.getAggregateId());
        assertEquals(original.getEventType(), restored.getEventType());
        assertEquals(original.getOccurredAt(), restored.getOccurredAt());

        LessonEventPayload restoredPayload = restored.getPayloadAs(LessonEventPayload.class);
        assertNotNull(restoredPayload);
        assertEquals("PPT", restoredPayload.getLessonType());
        assertEquals("Lesson 1", restoredPayload.getTitle());
        assertEquals(Integer.valueOf(1), restoredPayload.getSortOrder());
    }

    @Test
    void getPayloadAs_returns_null_for_null_payload() {
        DomainEvent ev = new DomainEvent()
                .setEventId(UUID.randomUUID().toString());
        assertNull(ev.getPayloadAs(LessonEventPayload.class));
    }

    @Test
    void empty_event_serializes_with_NonNull_excludes_nulls() {
        DomainEvent ev = new DomainEvent()
                .setEventId(UUID.randomUUID().toString())
                .setAggregateType("COURSE");

        String json = ev.toJsonPayload();
        assertTrue(json.contains("\"eventId\""));
        assertTrue(json.contains("\"aggregateType\":\"COURSE\""));
        assertFalse(json.contains("traceId"), "traceId 空值应被 @JsonInclude 过滤");
        assertFalse(json.contains("payload"));
    }
}
