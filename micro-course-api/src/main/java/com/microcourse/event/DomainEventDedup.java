package com.microcourse.event;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * V314 domain_event_dedup 表 MyBatis-Plus 实体.
 * P1 spec §三.3.2: event_id 共享 V313 outbox.event_id 命名空间.
 *
 * 手写 getter/setter (项目禁用 Lombok 注解处理器).
 */
@TableName("domain_event_dedup")
public class DomainEventDedup {

    @TableId(type = IdType.INPUT)
    private String eventId;
    private String source;
    private String traceId;
    private LocalDateTime processedAt;
    private LocalDateTime createdAt;

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
