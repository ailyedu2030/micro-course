package com.microcourse.event.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CourseEventPayload - Course 事件 payload. P1 spec §四.4.1 + §六.6.1.
 * Hermes 期望的字段子集 (从 HermesCourseDetailVO 抽出避免信息泄漏).
 *
 * 手写 getter/setter (项目禁用 Lombok 注解处理器).
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
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
