package com.microcourse.event;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * V315 domain_event_dead_letter 表 MyBatis-Plus 实体.
 * P1 spec §三.3.3: 5 次重试均失败的事件最终落地点.
 *
 * 后续操作由 DeadLetterService 接管 (countUnacked / acknowledge / forceRessurect).
 * 手写 getter/setter (项目禁用 Lombok 注解处理器).
 */
@TableName("domain_event_dead_letter")
public class DomainEventDeadLetter {

    @TableId(type = IdType.INPUT)
    private String eventId;
    private String aggregateType;
    private Long aggregateId;
    private String payload;
    private String lastError;
    private LocalDateTime failedAt;
    private String operator;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime createdAt;

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }

    public Long getAggregateId() { return aggregateId; }
    public void setAggregateId(Long aggregateId) { this.aggregateId = aggregateId; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public LocalDateTime getFailedAt() { return failedAt; }
    public void setFailedAt(LocalDateTime failedAt) { this.failedAt = failedAt; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(LocalDateTime acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
