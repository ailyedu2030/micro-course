package com.microcourse.event;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * V313 domain_event_outbox 表 MyBatis-Plus 实体.
 * P1 spec §三.3.1: event_id 是 UUID v4 PK; payload 是 Jackson 序列化的完整 DomainEvent.
 *
 * 手写 getter/setter (项目禁用 Lombok 注解处理器).
 */
@TableName("domain_event_outbox")
public class DomainEventOutbox {

    @TableId(type = IdType.INPUT)
    private String eventId;
    private String aggregateType;
    private Long aggregateId;
    private String eventType;
    private String payload;
    private String traceId;
    private LocalDateTime occurredAt;
    private OutboxStatus status;
    private Integer attemptCount;
    private LocalDateTime nextAttemptAt;
    private String lastError;
    private LocalDateTime deliveredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }

    public Long getAggregateId() { return aggregateId; }
    public void setAggregateId(Long aggregateId) { this.aggregateId = aggregateId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }

    public OutboxStatus getStatus() { return status; }
    public void setStatus(OutboxStatus status) { this.status = status; }

    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }

    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(LocalDateTime nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
