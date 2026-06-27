package com.microcourse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * SlidePage — 课件单页（从 course_slides.pdf/ppt 转换的图片）。
 * R8 P1-I #7: 补全 V49 创建的 slide_pages 表对应 Entity。
 *
 * @see docs/数据字典.md §2.18
 */
@TableName("slide_pages")
public class SlidePage {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("slide_id")
    private Long slideId;

    @TableField("course_id")
    private Long courseId;

    @TableField("page_number")
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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
