package com.microcourse.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDateTime;

/**
 * DomainEvent 域事件 POJO. P1 spec §三.3.4.
 *
 * 用途:
 *   - 序列化到 V313 domain_event_outbox.payload (Jackson 完整 JSON)
 *   - 通过 HermesEventPushClient 推到 Hermes
 *   - Hermes 反推过来, 反序列化回 LocalDateTime + 业务 payload
 *
 * 关键字段:
 *   event_id: UUID v4, 双向幂等键 (V314 dedup.event_id)
 *   trace_id: 全链路追踪, 用于排错与日志聚合
 *   occurred_at: 业务发生时刻 (不是 db insert 时刻)
 *   aggregate_type: COURSE / CHAPTER / SECTION / LESSON
 *   aggregate_id: 对应主键
 *   event_type: CREATED / UPDATED / DELETED / REORDERED
 *   action: HTTP_POST / HTTP_PUT / HTTP_DELETE
 *   endpoint: Hermes 端 REST 路径
 *   hermesCourseId: Hermes 课程映射 ID
 *   payload: *EventPayload 类型实例 (实体), Jackson 序列化为嵌套
 *
 * 手写 getter/setter (项目禁用 Lombok 注解处理器).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DomainEvent {

    private String eventId;
    private String traceId;
    private LocalDateTime occurredAt;
    private String aggregateType;
    private Long aggregateId;
    private String eventType;
    private String action;
    private String endpoint;
    private String hermesCourseId;
    private Object payload;

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }

    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }

    public Long getAggregateId() { return aggregateId; }
    public void setAggregateId(Long aggregateId) { this.aggregateId = aggregateId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getHermesCourseId() { return hermesCourseId; }
    public void setHermesCourseId(String hermesCourseId) { this.hermesCourseId = hermesCourseId; }

    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }

    /** 共享 ObjectMapper: 支持 LocalDateTime + Builder-to-Record 不含. */
    private static final ObjectMapper M = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public String toJsonPayload() {
        try {
            return M.writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException("serialize DomainEvent failed", e);
        }
    }

    public static DomainEvent fromJsonPayload(String json) {
        try {
            return M.readValue(json, DomainEvent.class);
        } catch (Exception e) {
            throw new RuntimeException("deserialize DomainEvent failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getPayloadAs(Class<T> clazz) {
        if (payload == null) {
            return null;
        }
        return M.convertValue(payload, clazz);
    }
}
