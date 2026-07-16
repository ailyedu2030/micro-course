package com.microcourse.dto;

import java.time.LocalDateTime;
import java.util.List;

public class SectionDTO {
    private Long id;
    private Long chapterId;
    private Long courseId;
    private String title;
    private String sectionType;
    private Integer sortOrder;
    private Integer duration;
    private Boolean visible;
    private String description;
    private String scriptContent;
    private String contentUrl;
    private Integer slideCount;
    private Boolean hasSlide;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ===== P1 Stage 1: 小节级元信息(交叉审查 P1-1:VO 必须包含新字段) =====
    private String no;
    private List<String> learningObjectives;
    private String anchorScenarioStep;
    private String coreCompetency;
    private String coursewareType;
    private String audioStrategy;

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
    public Integer getSlideCount() { return slideCount; }
    public void setSlideCount(Integer slideCount) { this.slideCount = slideCount; }
    public Boolean getHasSlide() { return hasSlide; }
    public void setHasSlide(Boolean hasSlide) { this.hasSlide = hasSlide; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    // ===== P1 Stage 1 getters/setters =====
    public String getNo() { return no; }
    public void setNo(String no) { this.no = no; }
    public List<String> getLearningObjectives() { return learningObjectives; }
    public void setLearningObjectives(List<String> learningObjectives) { this.learningObjectives = learningObjectives; }
    public String getAnchorScenarioStep() { return anchorScenarioStep; }
    public void setAnchorScenarioStep(String anchorScenarioStep) { this.anchorScenarioStep = anchorScenarioStep; }
    public String getCoreCompetency() { return coreCompetency; }
    public void setCoreCompetency(String coreCompetency) { this.coreCompetency = coreCompetency; }
    public String getCoursewareType() { return coursewareType; }
    public void setCoursewareType(String coursewareType) { this.coursewareType = coursewareType; }
    public String getAudioStrategy() { return audioStrategy; }
    public void setAudioStrategy(String audioStrategy) { this.audioStrategy = audioStrategy; }
}
