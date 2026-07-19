package com.microcourse.plugin.interactive.dto;

import java.time.LocalDateTime;

/**
 * HTML 课件单元 DTO (V303 slide_html_units).
 */
public class SlideHtmlUnitDTO {
    private Long id;
    private Long courseId;
    private Long chapterId;
    private Long sectionId;
    private Long slideId;
    private String pageTitle;
    private String fileUuid;
    private String htmlContent;
    private String htmlSanitized;
    private Long fileSizeBytes;
    private Integer detectedSegments;
    private Boolean hasInteractions;
    private String interactionTypes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public Long getSectionId() { return sectionId; }
    public void setSectionId(Long sectionId) { this.sectionId = sectionId; }
    public Long getSlideId() { return slideId; }
    public void setSlideId(Long slideId) { this.slideId = slideId; }
    public String getPageTitle() { return pageTitle; }
    public void setPageTitle(String pageTitle) { this.pageTitle = pageTitle; }
    public String getFileUuid() { return fileUuid; }
    public void setFileUuid(String fileUuid) { this.fileUuid = fileUuid; }
    public String getHtmlContent() { return htmlContent; }
    public void setHtmlContent(String htmlContent) { this.htmlContent = htmlContent; }
    public String getHtmlSanitized() { return htmlSanitized; }
    public void setHtmlSanitized(String htmlSanitized) { this.htmlSanitized = htmlSanitized; }
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
    public Integer getDetectedSegments() { return detectedSegments; }
    public void setDetectedSegments(Integer detectedSegments) { this.detectedSegments = detectedSegments; }
    public Boolean getHasInteractions() { return hasInteractions; }
    public void setHasInteractions(Boolean hasInteractions) { this.hasInteractions = hasInteractions; }
    public String getInteractionTypes() { return interactionTypes; }
    public void setInteractionTypes(String interactionTypes) { this.interactionTypes = interactionTypes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}