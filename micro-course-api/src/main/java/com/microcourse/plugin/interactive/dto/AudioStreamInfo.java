package com.microcourse.plugin.interactive.dto;

/**
 * 音频流信息 (用于 audio_token 流式 GET endpoint).
 * 由 CoursewareQueryService.resolveAudioToken 返回.
 */
public class AudioStreamInfo {
    private String token;
    private String audioUrl;
    private Long courseId;
    private String coursewareType;  // "PPT" / "HTML"
    private Long ownerId;           // pptPageId or htmlUnitId
    private Long scriptId;
    private Long segmentIndex;      // 仅 HTML 用
    private Integer audioDurationMs;
    private String status;
    private String storagePath;
    private Long fileSizeBytes;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getAudioUrl() { return audioUrl; }
    public void setAudioUrl(String audioUrl) { this.audioUrl = audioUrl; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getCoursewareType() { return coursewareType; }
    public void setCoursewareType(String coursewareType) { this.coursewareType = coursewareType; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public Long getScriptId() { return scriptId; }
    public void setScriptId(Long scriptId) { this.scriptId = scriptId; }
    public Long getSegmentIndex() { return segmentIndex; }
    public void setSegmentIndex(Long segmentIndex) { this.segmentIndex = segmentIndex; }
    public Integer getAudioDurationMs() { return audioDurationMs; }
    public void setAudioDurationMs(Integer audioDurationMs) { this.audioDurationMs = audioDurationMs; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
}