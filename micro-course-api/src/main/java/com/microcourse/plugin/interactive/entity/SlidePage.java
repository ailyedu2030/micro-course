package com.microcourse.plugin.interactive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 互动课件页面实体 (slide_pages 表).
 * <p>
 * V49 阶段由 plugin.interactive 插件创建。V177 阶段新增 contentType 和 htmlContent 字段支持 HTML 课件。
 * 该实体是 slide_pages 表的唯一活跃 Entity（com.microcourse.entity.SlidePage 已在 Phase 14.5 清理）。
 * </p>
 */
@TableName("slide_pages")
public class SlidePage {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("slide_id")
    private Long slideId;

    @TableField("chapter_id")
    private Long chapterId;

    @TableField("lesson_id")
    private Long lessonId;

    @TableField("section_id")
    private Long sectionId;

    @TableField("course_id")
    private Long courseId;

    private Integer pageNumber;

    @TableField("image_url")
    private String imageUrl;

    @TableField("thumbnail_url")
    private String thumbnailUrl;

    @TableField("image_width")
    private Integer imageWidth;

    @TableField("image_height")
    private Integer imageHeight;

    @TableField("extracted_text")
    private String extractedText;

    @TableField("has_animation")
    private Boolean hasAnimation;

    @TableField("has_embedded_media")
    private Boolean hasEmbeddedMedia;

    @TableField("content_type")
    private String contentType;

    @TableField("html_content")
    private String htmlContent;

    @TableField("file_uuid")
    private String fileUuid;

    @TableField("narration_script")
    private String narrationScript;

    @TableField("narration_audio_url")
    private String narrationAudioUrl;

    @TableField("audio_duration")
    private Integer audioDuration;

    @TableField("narration_status")
    private String narrationStatus;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public SlidePage() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSlideId() { return slideId; }
    public void setSlideId(Long slideId) { this.slideId = slideId; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public Long getLessonId() { return lessonId; }
    public void setLessonId(Long lessonId) { this.lessonId = lessonId; }
    public Long getSectionId() { return sectionId; }
    public void setSectionId(Long sectionId) { this.sectionId = sectionId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }
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
    public void setHasAnimation(Boolean hasAnimation) { this.hasAnimation = this.hasAnimation; }
    public Boolean getHasEmbeddedMedia() { return hasEmbeddedMedia; }
    public void setHasEmbeddedMedia(Boolean hasEmbeddedMedia) { this.hasEmbeddedMedia = hasEmbeddedMedia; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getHtmlContent() { return htmlContent; }
    public void setHtmlContent(String htmlContent) { this.htmlContent = htmlContent; }
    public String getFileUuid() { return fileUuid; }
    public void setFileUuid(String fileUuid) { this.fileUuid = fileUuid; }
    public String getNarrationScript() { return narrationScript; }
    public void setNarrationScript(String narrationScript) { this.narrationScript = narrationScript; }
    public String getNarrationAudioUrl() { return narrationAudioUrl; }
    public void setNarrationAudioUrl(String narrationAudioUrl) { this.narrationAudioUrl = narrationAudioUrl; }
    public Integer getAudioDuration() { return audioDuration; }
    public void setAudioDuration(Integer audioDuration) { this.audioDuration = audioDuration; }
    public String getNarrationStatus() { return narrationStatus; }
    public void setNarrationStatus(String narrationStatus) { this.narrationStatus = narrationStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
