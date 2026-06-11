package com.microcourse.dto;

public class ProgressUpdateRequest {

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

    public ProgressUpdateRequest() {}

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
}