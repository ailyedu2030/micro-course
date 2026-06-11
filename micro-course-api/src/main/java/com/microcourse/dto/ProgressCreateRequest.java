package com.microcourse.dto;

import jakarta.validation.constraints.NotNull;

public class ProgressCreateRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "课程ID不能为空")
    private Long courseId;

    private Long chapterId;

    private Integer videoProgress;

    private Integer videoPosition;

    private Boolean exerciseCompleted;

    private Boolean exercisePassed;

    private Integer totalWatchTime;

    private String deviceId;

    private String platform;

    private Double playbackSpeed;

    private Integer confidence;

    public ProgressCreateRequest() {}

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
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
}