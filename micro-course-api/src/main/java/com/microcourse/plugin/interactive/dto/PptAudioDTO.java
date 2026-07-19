package com.microcourse.plugin.interactive.dto;

import java.time.LocalDateTime;

public class PptAudioDTO {
    private Long id;
    private Long scriptId;
    private Long pptPageId;
    private String audioUrl;
    private String audioToken;
    private Integer audioDurationMs;
    private String voiceUsed;
    private String modelUsed;
    private String generationParams;
    private LocalDateTime generationStartedAt;
    private LocalDateTime completedAt;
    private String status;
    private Long fileSizeBytes;
    private String storagePath;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getScriptId() { return scriptId; }
    public void setScriptId(Long scriptId) { this.scriptId = scriptId; }
    public Long getPptPageId() { return pptPageId; }
    public void setPptPageId(Long pptPageId) { this.pptPageId = pptPageId; }
    public String getAudioUrl() { return audioUrl; }
    public void setAudioUrl(String audioUrl) { this.audioUrl = audioUrl; }
    public String getAudioToken() { return audioToken; }
    public void setAudioToken(String audioToken) { this.audioToken = audioToken; }
    public Integer getAudioDurationMs() { return audioDurationMs; }
    public void setAudioDurationMs(Integer audioDurationMs) { this.audioDurationMs = audioDurationMs; }
    public String getVoiceUsed() { return voiceUsed; }
    public void setVoiceUsed(String voiceUsed) { this.voiceUsed = voiceUsed; }
    public String getModelUsed() { return modelUsed; }
    public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }
    public String getGenerationParams() { return generationParams; }
    public void setGenerationParams(String generationParams) { this.generationParams = generationParams; }
    public LocalDateTime getGenerationStartedAt() { return generationStartedAt; }
    public void setGenerationStartedAt(LocalDateTime generationStartedAt) { this.generationStartedAt = generationStartedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}