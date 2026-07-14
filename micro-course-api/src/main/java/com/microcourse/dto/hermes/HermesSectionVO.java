package com.microcourse.dto.hermes;

import java.time.LocalDateTime;

public class HermesSectionVO {

    private Long id;
    private Long chapterId;
    private String title;
    private String sectionType;
    private Integer sortOrder;
    private Integer duration;
    private Boolean visible;
    private String description;
    private String scriptContent;
    private String contentUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public HermesSectionVO() {}

    public HermesSectionVO(Long id, Long chapterId, String title, String sectionType,
                          Integer sortOrder, Integer duration, Boolean visible,
                          String description, String scriptContent, String contentUrl,
                          LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.chapterId = chapterId;
        this.title = title;
        this.sectionType = sectionType;
        this.sortOrder = sortOrder;
        this.duration = duration;
        this.visible = visible;
        this.description = description;
        this.scriptContent = scriptContent;
        this.contentUrl = contentUrl;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
