package com.microcourse.dto;

/**
 * 视频进度上报请求 DTO (替代 Map&lt;String, Object&gt;)
 *
 * <p>对应 POST /api/videos/{id}/progress 端点</p>
 */
public class VideoProgressReportRequest {

    /** 视频进度百分比 (0-100) */
    private Integer videoProgress;
    /** 视频当前位置 (秒) */
    private Integer videoPosition;
    /** 总观看时长 (秒) */
    private Integer totalWatchTime;
    /** 播放速度 (0.75/1.0/1.25/1.5/2.0, 可选) */
    private Double playbackSpeed;
    /** 平台 (web/ios/android, 可选) */
    private String platform;
    /** 设备 ID (可选) */
    private String deviceId;
    /** 信心度 (0-1, 可选) */
    private Double confidence;

    public Integer getVideoProgress() { return videoProgress; }
    public void setVideoProgress(Integer videoProgress) { this.videoProgress = videoProgress; }
    public Integer getVideoPosition() { return videoPosition; }
    public void setVideoPosition(Integer videoPosition) { this.videoPosition = videoPosition; }
    public Integer getTotalWatchTime() { return totalWatchTime; }
    public void setTotalWatchTime(Integer totalWatchTime) { this.totalWatchTime = totalWatchTime; }
    public Double getPlaybackSpeed() { return playbackSpeed; }
    public void setPlaybackSpeed(Double playbackSpeed) { this.playbackSpeed = playbackSpeed; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
}