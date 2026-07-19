package com.microcourse.plugin.interactive.dto;

import java.time.LocalDateTime;

/**
 * PPT 课件页面 DTO (V300 slide_ppt_pages).
 */
public class SlidePptPageDTO {
    private Long id;
    private Long courseId;
    private Long chapterId;
    private Long sectionId;
    private Long slideId;
    private Integer pageNumber;
    private String pageTitle;
    private String imageUrl;
    private String thumbnailUrl;
    private Integer imageWidth;
    private Integer imageHeight;
    private String fileUuid;
    private Long fileSizeBytes;
    private String extractedText;
    private Boolean hasAnimation;
    private Boolean hasEmbeddedMedia;
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
    public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }
    public String getPageTitle() { return pageTitle; }
    public void setPageTitle(String pageTitle) { this.pageTitle = pageTitle; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    public Integer getImageWidth() { return imageWidth; }
    public void setImageWidth(Integer imageWidth) { this.imageWidth = imageWidth; }
    public Integer getImageHeight() { return imageHeight; }
    public void setImageHeight(Integer imageHeight) { this.imageHeight = imageHeight; }
    public String getFileUuid() { return fileUuid; }
    public void setFileUuid(String fileUuid) { this.fileUuid = fileUuid; }
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String extractedText) { this.extractedText = extractedText; }
    public Boolean getHasAnimation() { return hasAnimation; }
    public void setHasAnimation(Boolean hasAnimation) { this.hasAnimation = hasAnimation; }
    public Boolean getHasEmbeddedMedia() { return hasEmbeddedMedia; }
    public void setHasEmbeddedMedia(Boolean hasEmbeddedMedia) { this.hasEmbeddedMedia = hasEmbeddedMedia; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}