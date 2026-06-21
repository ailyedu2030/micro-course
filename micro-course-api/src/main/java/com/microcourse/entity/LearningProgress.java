package com.microcourse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

@TableName("learning_progress")
public class LearningProgress {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("course_id")
    private Long courseId;

    @TableField("chapter_id")
    private Long chapterId;

    @TableField("lesson_id")
    private Long lessonId;

    @TableField("video_progress")
    private Integer videoProgress;

    @TableField("video_position")
    private Integer videoPosition;

    @TableField("exercise_completed")
    private Boolean exerciseCompleted;

    @TableField("exercise_passed")
    private Boolean exercisePassed;

    @TableField("total_watch_time")
    private Integer totalWatchTime;

    @TableField("device_id")
    private String deviceId;

    private String platform;

    @TableField("playback_speed")
    private Double playbackSpeed;

    private Integer confidence;

    private Boolean completed;

    @TableField("last_watch_at")
    private LocalDateTime lastWatchAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /**
     * 乐观锁版本号 —— P0-6 修复：防止同用户多端并发上报进度时丢失更新。
     * 对应 DB 列由 V62__learning_progress_add_version.sql 增量补齐（NOT NULL DEFAULT 0）。
     * 注意：updateProgress 走 update(null, wrapper) 原子 SQL 累加（CON-003），
     *      wrapperMode 默认关闭，故并发上报全部成功且累加；@Version 守护 updateById 实体更新路径。
     */
    @Version
    @TableField("version")
    private Integer version;

    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;

    public LearningProgress() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public Long getLessonId() { return lessonId; }
    public void setLessonId(Long lessonId) { this.lessonId = lessonId; }
    public Integer getVideoProgress() { return videoProgress; }
    public void setVideoProgress(Integer videoProgress) { this.videoProgress = videoProgress; }
    public Integer getVideoPosition() { return videoPosition; }
    public void setVideoPosition(Integer videoPosition) { this.videoPosition = videoPosition; }
    public Boolean getExerciseCompleted() { return exerciseCompleted; }
    public void setExerciseCompleted(Boolean exerciseCompleted) { this.exerciseCompleted = exerciseCompleted; }
    public Boolean getExercisePassed() { return exercisePassed; }
    public void setExercisePassed(Boolean exercisePassed) { this.exercisePassed = exercisePassed; }
    public Integer getTotalWatchTime() { return totalWatchTime; }
    public void setTotalWatchTime(Integer totalWatchTime) { this.totalWatchTime = totalWatchTime; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public Double getPlaybackSpeed() { return playbackSpeed; }
    public void setPlaybackSpeed(Double playbackSpeed) { this.playbackSpeed = playbackSpeed; }
    public Integer getConfidence() { return confidence; }
    public void setConfidence(Integer confidence) { this.confidence = confidence; }
    public Boolean getCompleted() { return completed; }
    public void setCompleted(Boolean completed) { this.completed = completed; }
    public LocalDateTime getLastWatchAt() { return lastWatchAt; }
    public void setLastWatchAt(LocalDateTime lastWatchAt) { this.lastWatchAt = lastWatchAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}