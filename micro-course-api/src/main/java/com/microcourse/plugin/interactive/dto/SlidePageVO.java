package com.microcourse.plugin.interactive.dto;

import java.time.LocalDateTime;

public class SlidePageVO {

    private Long id;
    private Long slideId;
    private Long chapterId;
    private Long sectionId;
    private Long courseId;
    private Integer pageNumber;
    private String fileUuid;
    private String imageUrl;
    private String thumbnailUrl;
    private Integer imageWidth;
    private Integer imageHeight;
    private String extractedText;
    private Boolean hasAnimation;
    private Boolean hasEmbeddedMedia;
    private String narrationScript;
    private String narrationAudioUrl;
    private Integer audioDuration;
    private String narrationStatus;
    private String narrationStatusText;
    private String contentType;
    private String htmlContent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public SlidePageVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSlideId() { return slideId; }
    public void setSlideId(Long slideId) { this.slideId = slideId; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public Long getSectionId() { return sectionId; }
    public void setSectionId(Long sectionId) { this.sectionId = sectionId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }
    public String getFileUuid() { return fileUuid; }
    public void setFileUuid(String fileUuid) { this.fileUuid = fileUuid; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    public Integer getImageWidth() { return imageWidth; }
    public void setImageWidth(Integer imageWidth) { this.imageWidth = imageWidth; }
    public Integer getImageHeight() { return imageHeight; }
    public void setImageHeight(Integer imageHeight) { this.imageHeight = imageHeight; }
    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String extractedText) { this.extractedText = extractedText; }
    public Boolean getHasAnimation() { return hasAnimation; }
    public void setHasAnimation(Boolean hasAnimation) { this.hasAnimation = hasAnimation; }
    public Boolean getHasEmbeddedMedia() { return hasEmbeddedMedia; }
    public void setHasEmbeddedMedia(Boolean hasEmbeddedMedia) { this.hasEmbeddedMedia = hasEmbeddedMedia; }
    public String getNarrationScript() { return narrationScript; }
    public void setNarrationScript(String narrationScript) { this.narrationScript = narrationScript; }
    public String getNarrationAudioUrl() { return narrationAudioUrl; }
    public void setNarrationAudioUrl(String narrationAudioUrl) { this.narrationAudioUrl = narrationAudioUrl; }
    public Integer getAudioDuration() { return audioDuration; }
    public void setAudioDuration(Integer audioDuration) { this.audioDuration = audioDuration; }
    public String getNarrationStatus() { return narrationStatus; }
    public void setNarrationStatus(String narrationStatus) { this.narrationStatus = narrationStatus; }
    public String getNarrationStatusText() { return narrationStatusText; }
    public void setNarrationStatusText(String narrationStatusText) { this.narrationStatusText = narrationStatusText; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getHtmlContent() { return htmlContent; }
    public void setHtmlContent(String htmlContent) { this.htmlContent = htmlContent; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public static String narrationStatusText(String status) {
        if (status == null) return "待处理";
        switch (status) {
            case "PENDING": return "待处理";
            case "AI_GENERATED": return "AI 已生成";
            case "TEACHER_EDITED": return "教师已编辑";
            case "AUDIO_GENERATING": return "音频生成中";
            case "AUDIO_READY": return "音频就绪";
            default: return status;
        }
    }
}
