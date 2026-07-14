package com.microcourse.dto;

import com.microcourse.enums.VideoStatus;

public class VideoStatusVO {

    private Long videoId;
    private Integer status;
    private String statusLabel;
    private Integer progress;
    private String errorMessage;

    public VideoStatusVO() {}

    public VideoStatusVO(Long videoId, Integer status, Integer progress, String errorMessage) {
        this.videoId = videoId;
        this.status = status;
        this.progress = progress;
        this.errorMessage = errorMessage;
        if (status != null) {
            try {
                this.statusLabel = VideoStatus.fromCode(status).getLabel();
            } catch (IllegalArgumentException e) {
                this.statusLabel = "未知状态";
            }
        }
    }

    public Long getVideoId() { return videoId; }
    public void setVideoId(Long videoId) { this.videoId = videoId; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getStatusLabel() { return statusLabel; }
    public void setStatusLabel(String statusLabel) { this.statusLabel = statusLabel; }
    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}