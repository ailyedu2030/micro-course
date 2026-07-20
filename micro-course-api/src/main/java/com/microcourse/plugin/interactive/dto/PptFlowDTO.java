package com.microcourse.plugin.interactive.dto;

import java.time.LocalDateTime;

public class PptFlowDTO {
    private Long id;
    private Long sectionId;
    private Long fromPageId;
    private Long toPageId;
    private String flowType;
    private Integer priority;
    private Long dependsOnQuizId;
    private String conditionExpression;
    private String description;
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