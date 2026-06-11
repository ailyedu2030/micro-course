package com.microcourse.dto;

import java.time.LocalDateTime;

public class LearningProgressVO {

    private Long id;
    private Long userId;
    private Long courseId;
    private String courseTitle;
    private Long chapterId;
    private String chapterTitle;
    private Integer videoProgress;
    private Integer videoPosition;
    private Integer exerciseCompleted;
    private Integer exercisePassed;
    private Integer totalWatchTime;
    private String deviceId;
    private String platform;
    private Double playbackSpeed;
    private Integer confidence;
    private Boolean completed;
    private LocalDateTime lastWatchAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public LearningProgressVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getCourseTitle() { return courseTitle; }
    public void setCourseTitle(String courseTitle) { this.courseTitle = courseTitle; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public String getChapterTitle() { return chapterTitle; }
    public void setChapterTitle(String chapterTitle) { this.chapterTitle = chapterTitle; }
    public Integer getVideoProgress() { return videoProgress; }
    public void setVideoProgress(Integer videoProgress) { this.videoProgress = videoProgress; }
    public Integer getVideoPosition() { return videoPosition; }
    public void setVideoPosition(Integer videoPosition) { this.videoPosition = videoPosition; }
    public Integer getExerciseCompleted() { return exerciseCompleted; }
    public void setExerciseCompleted(Integer exerciseCompleted) { this.exerciseCompleted = exerciseCompleted; }
    public Integer getExercisePassed() { return exercisePassed; }
    public void setExercisePassed(Integer exercisePassed) { this.exercisePassed = exercisePassed; }
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
}