package com.microcourse.event.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CourseEventPayload - Course 事件 payload. P1 spec §四.4.1 + §六.6.1.
 *
 * 手写 getter/setter + 链式 fluent API (项目禁用 Lombok 注解处理器,
 * 与既有所有 entity 风格一致).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CourseEventPayload {

    private Long id;
    private String title;
    private String subtitle;
    private String summary;
    private BigDecimal price;
    private Integer status;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public CourseEventPayload setId(Long id) { this.id = id; return this; }
    public String getTitle() { return title; }
    public CourseEventPayload setTitle(String title) { this.title = title; return this; }
    public String getSubtitle() { return subtitle; }
    public CourseEventPayload setSubtitle(String subtitle) { this.subtitle = subtitle; return this; }
    public String getSummary() { return summary; }
    public CourseEventPayload setSummary(String summary) { this.summary = summary; return this; }
    public BigDecimal getPrice() { return price; }
    public CourseEventPayload setPrice(BigDecimal price) { this.price = price; return this; }
    public Integer getStatus() { return status; }
    public CourseEventPayload setStatus(Integer status) { this.status = status; return this; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public CourseEventPayload setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }
}
