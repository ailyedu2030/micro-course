package com.microcourse.dto;

import java.time.LocalDateTime;

public class LearningProgressVO {

    private Long id;
    private Long userId;
    private Long courseId;
    private String courseTitle;
    private Long chapterId;
    private String chapterTitle;
    private Long sectionId;
    private Integer videoProgress;
    private Integer videoPosition;
    private Boolean exerciseCompleted;
    private Boolean exercisePassed;
    private Integer totalWatchTime;
    private String deviceId;
    private String platform;
    private Double playbackSpeed;
    private Integer confidence;
    private Boolean completed;
    private LocalDateTime lastWatchAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // P1C-021: 线下活动是否签到
    private Boolean offlineAttended;

    // P0-4: 课程级练习完成统计（由 Service 层聚合填充）
    private Integer completedExercises;
    private Integer totalExercises;

    // 课程级已完成视频数：count(completed=true AND section_id IS NOT NULL)
    private Integer completedVideos;

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
    public Long getSectionId() { return sectionId; }
    public void setSectionId(Long sectionId) { this.sectionId = sectionId; }
    public Integer getVideoProgress() { return videoProgress; }
    public void setVideoProgress(Integer videoProgress) { this.videoProgress = videoProgress; }
    public Integer getVideoPosition() { return videoPosition; }
    public void setVideoPosition(Integer videoPosition) { this.videoPosition = videoPosition; }
    public Boolean getExerciseCompleted() { return exerciseCompleted; }
    public void setExerciseCompleted(Boolean exerciseCompleted) { this.exerciseCompleted = exerciseCompleted; }
    public Boolean getExercisePassed() { return exercisePassed; }
    public Boolean getOfflineAttended() { return offlineAttended; }
    public void setOfflineAttended(Boolean offlineAttended) { this.offlineAttended = offlineAttended; }
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
    public Integer getCompletedExercises() { return completedExercises; }
    public void setCompletedExercises(Integer completedExercises) { this.completedExercises = completedExercises; }
    public Integer getTotalExercises() { return totalExercises; }
    public void setTotalExercises(Integer totalExercises) { this.totalExercises = totalExercises; }
    public Integer getCompletedVideos() { return completedVideos; }
    public void setCompletedVideos(Integer completedVideos) { this.completedVideos = completedVideos; }
}