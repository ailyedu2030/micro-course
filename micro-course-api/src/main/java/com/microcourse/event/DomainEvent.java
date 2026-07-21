package com.microcourse.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDateTime;

/**
 * DomainEvent 域事件 POJO. P1 spec §三.3.4.
 *
 * 手写 getter/setter + 链式 fluent API (项目禁用 Lombok 注解处理器).
 * 用途:
 *   - 序列化到 V313 outbox.payload (Jackson 完整 JSON)
 *   - 推给 Hermes via HermesEventPushClient
 *   - Hermes 反推过来, 反序列化回 LocalDateTime + 业务 payload
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
    public DomainEvent setEventId(String eventId) { this.eventId = eventId; return this; }
    public String getTraceId() { return traceId; }
    public DomainEvent setTraceId(String traceId) { this.traceId = traceId; return this; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public DomainEvent setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; return this; }
    public String getAggregateType() { return aggregateType; }
    public DomainEvent setAggregateType(String aggregateType) { this.aggregateType = aggregateType; return this; }
    public Long getAggregateId() { return aggregateId; }
    public DomainEvent setAggregateId(Long aggregateId) { this.aggregateId = aggregateId; return this; }
    public String getEventType() { return eventType; }
    public DomainEvent setEventType(String eventType) { this.eventType = eventType; return this; }
    public String getAction() { return action; }
    public DomainEvent setAction(String action) { this.action = action; return this; }
    public String getEndpoint() { return endpoint; }
    public DomainEvent setEndpoint(String endpoint) { this.endpoint = endpoint; return this; }
    public String getHermesCourseId() { return hermesCourseId; }
    public DomainEvent setHermesCourseId(String hermesCourseId) { this.hermesCourseId = hermesCourseId; return this; }
    public Object getPayload() { return payload; }
    public DomainEvent setPayload(Object payload) { this.payload = payload; return this; }

    private static final ObjectMapper M = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public String toJsonPayload() {
        try {
            return M.writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException("serialize DomainEvent failed: " + e.getMessage(), e);
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
