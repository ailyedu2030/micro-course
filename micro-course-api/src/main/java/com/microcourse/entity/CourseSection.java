package com.microcourse.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("course_sections")
public class CourseSection {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("chapter_id") private Long chapterId;
    @TableField("course_id") private Long courseId;
    private String title;
    @TableField("section_type") private String sectionType;
    @TableField("sort_order") private Integer sortOrder;
    private Integer duration;
    private Boolean visible;
    private String description;
    @TableField("script_content") private String scriptContent;
    @TableField("content_url") private String contentUrl;
    @Version private Integer version;
    @TableField("created_at") private LocalDateTime createdAt;
    @TableField("updated_at") private LocalDateTime updatedAt;
    @TableLogic(value = "NULL", delval = "now()")
    @TableField("deleted_at") private LocalDateTime deletedAt;

    // ===== P1 Stage 1: 小节级元信息(Trae SKILL.md 模块 3.3) =====
    @TableField("no") private String no;
    @TableField("learning_objectives") private String learningObjectivesJson;
    @TableField("anchor_scenario_step") private String anchorScenarioStep;
    @TableField("core_competency") private String coreCompetency;
    @TableField("courseware_type") private String coursewareType;
    @TableField("audio_strategy") private String audioStrategy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSectionType() { return sectionType; }
    public void setSectionType(String sectionType) { this.sectionType = sectionType; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public Boolean getVisible() { return visible; }
    public void setVisible(Boolean visible) { this.visible = visible; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getScriptContent() { return scriptContent; }
    public void setScriptContent(String scriptContent) { this.scriptContent = scriptContent; }
    public String getContentUrl() { return contentUrl; }
    public void setContentUrl(String contentUrl) { this.contentUrl = contentUrl; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    // ===== P1 Stage 1 getters/setters =====
    public String getNo() { return no; }
    public void setNo(String no) { this.no = no; }
    public String getLearningObjectivesJson() { return learningObjectivesJson; }
    public void setLearningObjectivesJson(String learningObjectivesJson) { this.learningObjectivesJson = learningObjectivesJson; }
    public String getAnchorScenarioStep() { return anchorScenarioStep; }
    public void setAnchorScenarioStep(String anchorScenarioStep) { this.anchorScenarioStep = anchorScenarioStep; }
    public String getCoreCompetency() { return coreCompetency; }
    public void setCoreCompetency(String coreCompetency) { this.coreCompetency = coreCompetency; }
    public String getCoursewareType() { return coursewareType; }
    public void setCoursewareType(String coursewareType) { this.coursewareType = coursewareType; }
    public String getAudioStrategy() { return audioStrategy; }
    public void setAudioStrategy(String audioStrategy) { this.audioStrategy = audioStrategy; }
}
