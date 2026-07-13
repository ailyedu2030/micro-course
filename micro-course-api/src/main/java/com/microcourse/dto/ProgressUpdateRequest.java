package com.microcourse.dto;

public class ProgressUpdateRequest {

    private Integer videoProgress;

    private Integer videoPosition;

    private Boolean exerciseCompleted;

    private Boolean exercisePassed;

    private Integer totalWatchTime;

    /**
     * 本次上报增量观看秒数。用于多设备并发场景下,服务端做原子累加,避免并发写覆盖导致计数丢失。
     * 推荐前端使用此字段。若同时传 totalWatchTime 与 watchDelta,优先使用 watchDelta(忽略 totalWatchTime)。
     */
    private Integer watchDelta;

    private String deviceId;

    private String platform;

    private Double playbackSpeed;

    private Integer confidence;

    private Boolean completed;

    private Long sectionId;

    public ProgressUpdateRequest() {}

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
    public Integer getWatchDelta() { return watchDelta; }
    public void setWatchDelta(Integer watchDelta) { this.watchDelta = watchDelta; }
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
    public Long getSectionId() { return sectionId; }
    public void setSectionId(Long sectionId) { this.sectionId = sectionId; }
}