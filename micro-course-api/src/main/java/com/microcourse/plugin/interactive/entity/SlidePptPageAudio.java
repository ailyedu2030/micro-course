package com.microcourse.plugin.interactive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * PPT 课件页面音频实体 (slide_ppt_page_audios 表, V302).
 * 1 script : N 音频 (用于音色对比, audio_token 是 UK 校验).
 */
@TableName("slide_ppt_page_audios")
public class SlidePptPageAudio {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("script_id")
    private Long scriptId;

    @TableField("ppt_page_id")
    private Long pptPageId;

    @TableField("audio_url")
    private String audioUrl;

    @TableField("audio_token")
    private String audioToken;

    @TableField("audio_duration_ms")
    private Integer audioDurationMs;

    @TableField("voice_used")
    private String voiceUsed;

    @TableField("model_used")
    private String modelUsed;

    @TableField("generation_params")
    private String generationParams;

    @TableField("generation_started_at")
    private LocalDateTime generationStartedAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;

    private String status;

    @TableField("file_size_bytes")
    private Long fileSizeBytes;

    @TableField("storage_path")
    private String storagePath;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public SlidePptPageAudio() {}

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