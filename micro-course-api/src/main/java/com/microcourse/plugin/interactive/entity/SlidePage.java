package com.microcourse.plugin.interactive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("slide_pages")
public class SlidePage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long slideId;
    @TableField("chapter_id")
    private Long chapterId;
    private Long courseId;
    private Integer pageNumber;
    private String imageUrl;
    private String thumbnailUrl;
    private Integer imageWidth;
    private Integer imageHeight;
    private String extractedText;
    private Boolean hasAnimation;
    private Boolean hasEmbeddedMedia;
    private String fileUuid;
    private String narrationScript;
    private String narrationAudioUrl;
    private Integer audioDuration;
    private String narrationStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public SlidePage() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSlideId() { return slideId; }
    public void setSlideId(Long slideId) { this.slideId = slideId; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
