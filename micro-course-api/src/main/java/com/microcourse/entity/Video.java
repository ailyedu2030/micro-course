package com.microcourse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

@TableName("videos")
public class Video {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long chapterId;

    private Long courseId;

    private String title;

    @TableField("original_name")
    private String fileName;

    @TableField("file_size")
    private Long fileSize;

    @TableField("file_md5")
    private String fileMd5;

    @TableField("mime_type")
    private String mimeType;

    private Integer duration;

    private String url;

    @TableField("m3u8_url")
    private String hlsUrl;

    @TableField("thumbnail_url")
    private String thumbnailUrl;

    @TableField("cover_url")
    private String coverUrl;

    @TableField("play_sign")
    private String playSign;

    @TableField("sign_expired_at")
    private LocalDateTime signExpiredAt;

    @TableField("watermark_enabled")
    private Boolean watermarkEnabled;

    @TableField("max_play_rate")
    private Integer maxPlayRate;

    @TableField("caption_url")
    private String captionUrl;

    @TableField("caption_language")
    private String captionLanguage;

    @TableField("audio_description_url")
    private String audioDescriptionUrl;

    @TableField("allow_download")
    private Boolean allowDownload;

    private Integer status;

    private Integer progress;

    @TableField("error_message")
    private String errorMessage;

    @TableField("original_path")
    private String originalPath;

    @TableField("sort_order")
    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** P2: 乐观锁 */
    @Version
    private Integer version;

    @TableLogic(value = "NULL", delval = "now()")
    private LocalDateTime deletedAt;

    public Video() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getFileMd5() { return fileMd5; }
    public void setFileMd5(String fileMd5) { this.fileMd5 = fileMd5; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getHlsUrl() { return hlsUrl; }
    public void setHlsUrl(String hlsUrl) { this.hlsUrl = hlsUrl; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public String getPlaySign() { return playSign; }
    public void setPlaySign(String playSign) { this.playSign = playSign; }
    public LocalDateTime getSignExpiredAt() { return signExpiredAt; }
    public void setSignExpiredAt(LocalDateTime signExpiredAt) { this.signExpiredAt = signExpiredAt; }
    public Boolean getWatermarkEnabled() { return watermarkEnabled; }
    public void setWatermarkEnabled(Boolean watermarkEnabled) { this.watermarkEnabled = watermarkEnabled; }
    public Integer getMaxPlayRate() { return maxPlayRate; }
    public void setMaxPlayRate(Integer maxPlayRate) { this.maxPlayRate = maxPlayRate; }
    public String getCaptionUrl() { return captionUrl; }
    public void setCaptionUrl(String captionUrl) { this.captionUrl = captionUrl; }
    public String getCaptionLanguage() { return captionLanguage; }
    public void setCaptionLanguage(String captionLanguage) { this.captionLanguage = captionLanguage; }
    public String getAudioDescriptionUrl() { return audioDescriptionUrl; }
    public void setAudioDescriptionUrl(String audioDescriptionUrl) { this.audioDescriptionUrl = audioDescriptionUrl; }
    public Boolean getAllowDownload() { return allowDownload; }
    public void setAllowDownload(Boolean allowDownload) { this.allowDownload = allowDownload; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getOriginalPath() { return originalPath; }
    public void setOriginalPath(String originalPath) { this.originalPath = originalPath; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
