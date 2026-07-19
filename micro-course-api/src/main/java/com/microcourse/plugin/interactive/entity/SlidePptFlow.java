package com.microcourse.plugin.interactive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * PPT 课件页间跳转逻辑实体 (slide_ppt_flow 表, V306).
 * 三种 flow_type: NEXT (线性), BRANCH_DEPENDS (条件分支), SKIP_IF_KNOWN (智能跳过).
 */
@TableName("slide_ppt_flow")
public class SlidePptFlow {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("section_id")
    private Long sectionId;

    @TableField("from_page_id")
    private Long fromPageId;

    @TableField("to_page_id")
    private Long toPageId;

    @TableField("flow_type")
    private String flowType;

    private Integer priority;

    @TableField("depends_on_quiz_id")
    private Long dependsOnQuizId;

    @TableField("condition_expression")
    private String conditionExpression;

    private String description;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public SlidePptFlow() {}

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