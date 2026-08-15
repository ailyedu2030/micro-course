package com.microcourse.plugin.interactive.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * PPT 课件页面 DTO (V300 slide_ppt_pages).
 *
 * <p>P1-I-2026-08-15 · 补全字段级校验，配合 Controller {@code @Valid}：
 * <ul>
 *   <li>{@code slideId} + {@code pageNumber} 是必填主键</li>
 *   <li>URL/UUID 字段长度限制防止 DoS</li>
 *   <li>extractedText 限制 50000 字符（与 SlideServiceImpl TEMP_OFFSET 一致）</li>
 * </ul>
 */
public class SlidePptPageDTO {

    // slideId/pageNumber 仅 @Positive（允许 null）：updatePage 是 PATCH 部分更新，
    // slideId 由 path 决定并被 BeanUtils.copyProperties 显式忽略；create 时由 Service 校验
    @Positive(message = "slideId 必须为正数")
    private Long slideId;

    @Positive(message = "pageNumber 必须为正数")
    private Integer pageNumber;

    @Size(max = 200, message = "pageTitle 长度不能超过 200 字符")
    private String pageTitle;

    @Size(max = 500, message = "imageUrl 长度不能超过 500 字符")
    private String imageUrl;

    @Size(max = 500, message = "thumbnailUrl 长度不能超过 500 字符")
    private String thumbnailUrl;

    @Positive(message = "imageWidth 必须为正数")
    private Integer imageWidth;

    @Positive(message = "imageHeight 必须为正数")
    private Integer imageHeight;

    @Size(max = 64, message = "fileUuid 长度不能超过 64 字符")
    private String fileUuid;

    @Positive(message = "fileSizeBytes 必须为正数")
    private Long fileSizeBytes;

    @Size(max = 50000, message = "extractedText 长度不能超过 50000 字符")
    private String extractedText;

    private Long id;
    private Long courseId;
    private Long chapterId;
    private Long sectionId;
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