package com.microcourse.plugin.interactive.dto;

/**
 * PPT 页间跳转规则 VO（P0 聚合契约，方案 §5.1）。
 * 播放器按 fromPageId 建索引；flowType: NEXT / BRANCH_DEPENDS / SKIP_IF_KNOWN。
 */
public class PptFlowVO {

    private Long fromPageId;
    private Long toPageId;
    private String flowType;
    private Integer priority;
    private Long dependsOnQuizId;
    private String conditionExpression;
    private String description;

    public PptFlowVO() {}

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
}
