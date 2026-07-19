package com.microcourse.plugin.interactive.dto;

import java.time.LocalDateTime;

public class HtmlSegmentScriptDTO {
    private Long id;
    private Long htmlUnitId;
    private Integer segmentIndex;
    private String segmentMarker;
    private String segmentText;
    private String scriptText;
    private Integer scriptVersion;
    private Boolean isActive;
    private String voice;
    private String ttsModel;
    private String ttsParams;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getHtmlUnitId() { return htmlUnitId; }
    public void setHtmlUnitId(Long htmlUnitId) { this.htmlUnitId = htmlUnitId; }
    public Integer getSegmentIndex() { return segmentIndex; }
    public void setSegmentIndex(Integer segmentIndex) { this.segmentIndex = segmentIndex; }
    public String getSegmentMarker() { return segmentMarker; }
    public void setSegmentMarker(String segmentMarker) { this.segmentMarker = segmentMarker; }
    public String getSegmentText() { return segmentText; }
    public void setSegmentText(String segmentText) { this.segmentText = segmentText; }
    public String getScriptText() { return scriptText; }
    public void setScriptText(String scriptText) { this.scriptText = scriptText; }
    public Integer getScriptVersion() { return scriptVersion; }
    public void setScriptVersion(Integer scriptVersion) { this.scriptVersion = scriptVersion; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public String getVoice() { return voice; }
    public void setVoice(String voice) { this.voice = voice; }
    public String getTtsModel() { return ttsModel; }
    public void setTtsModel(String ttsModel) { this.ttsModel = ttsModel; }
    public String getTtsParams() { return ttsParams; }
    public void setTtsParams(String ttsParams) { this.ttsParams = ttsParams; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}