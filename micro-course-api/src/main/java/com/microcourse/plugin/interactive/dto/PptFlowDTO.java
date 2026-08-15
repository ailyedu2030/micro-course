package com.microcourse.plugin.interactive.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * PPT 翻页流程 DTO.
 *
 * <p>P1-I-2026-08-15 · 补全字段级校验，配合 Controller {@code @Valid}。
 */
public class PptFlowDTO {

    // P0-2026-08-15 · 校验修正（R4 审查）：
    // - sectionId 由 Controller 方法体 dto.setSectionId(sectionId) 从 path 填充，前端不传 → 允许 null
    // - toPageId 允许 null（V306 DB 明确 to_page_id BIGINT NULL = "课件结束" 语义）
    // - updateFlow 是 PATCH 部分更新 → 不允许 @NotNull 全字段必填
    // - 必填校验下沉 Service 层 createFlow（见 PptCoursewareServiceImpl）
    @Positive(message = "sectionId 必须为正数")
    private Long sectionId;

    @Positive(message = "fromPageId 必须为正数")
    private Long fromPageId;

    @Positive(message = "toPageId 必须为正数")
    private Long toPageId;

    @Size(max = 20, message = "flowType 长度不能超过 20 字符")
    private String flowType;

    @Positive(message = "priority 必须为正数")
    private Integer priority;

    @Positive(message = "dependsOnQuizId 必须为正数")
    private Long dependsOnQuizId;

    @Size(max = 500, message = "conditionExpression 长度不能超过 500 字符")
    private String conditionExpression;

    @Size(max = 500, message = "description 长度不能超过 500 字符")
    private String description;

    private Long id;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSectionId() { return sectionId; }
    public void setSectionId(Long sectionId) { this.sectionId = sectionId; }
    public Long getFromPageId() { return fromPageId; }
    public void setFromPageId(Long fromPageId) { this.fromPageId = fromPageId; }
    public Long getToPageId() { return toPageId; }
    public void setToPageId(Long toPageId) { this.toPageId = toPageId; }
    public String getFlowType() { return flowType; }
    public void setFlowType(String flowType) { this.flowType = flowType; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public Long getDependsOnQuizId() { return dependsOnQuizId; }
    public void setDependsOnQuizId(Long dependsOnQuizId) { this.dependsOnQuizId = dependsOnQuizId; }
    public String getConditionExpression() { return conditionExpression; }
    public void setConditionExpression(String conditionExpression) { this.conditionExpression = conditionExpression; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}