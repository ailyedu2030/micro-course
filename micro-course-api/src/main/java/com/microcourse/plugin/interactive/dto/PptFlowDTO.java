package com.microcourse.plugin.interactive.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * PPT 翻页流程 DTO.
 *
 * <p>P1-I-2026-08-15 · 补全字段级校验，配合 Controller {@code @Valid}。
 */
public class PptFlowDTO {

    @NotNull(message = "sectionId 不能为空")
    @Positive(message = "sectionId 必须为正数")
    private Long sectionId;

    @NotNull(message = "fromPageId 不能为空")
    @Positive(message = "fromPageId 必须为正数")
    private Long fromPageId;

    @NotNull(message = "toPageId 不能为空")
    @Positive(message = "toPageId 必须为正数")
    private Long toPageId;

    @NotBlank(message = "flowType 不能为空")
    @Size(max = 50, message = "flowType 长度不能超过 50 字符")
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