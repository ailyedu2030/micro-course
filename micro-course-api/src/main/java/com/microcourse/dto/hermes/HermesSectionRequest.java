package com.microcourse.dto.hermes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class HermesSectionRequest {

    private Long chapterId;

    @NotBlank(message = "title 不能为空")
    private String title;

    private String sectionType;

    private Integer sortOrder;

    private Integer duration;

    private Boolean visible = true;

    private String description;

    private String scriptContent;

    private String contentUrl;

    private Integer version;

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
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
