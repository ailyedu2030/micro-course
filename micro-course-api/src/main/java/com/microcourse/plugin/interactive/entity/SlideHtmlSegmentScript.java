package com.microcourse.plugin.interactive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * HTML 课件分段讲述稿实体 (slide_html_segment_scripts 表, V304).
 * 1 HTML unit : N segment (按 segment_index 排序, segment_marker 关联 DOM id).
 */
@TableName("slide_html_segment_scripts")
public class SlideHtmlSegmentScript {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("html_unit_id")
    private Long htmlUnitId;

    @TableField("segment_index")
    private Integer segmentIndex;

    @TableField("segment_marker")
    private String segmentMarker;

    @TableField("segment_text")
    private String segmentText;

    @TableField("script_text")
    private String scriptText;

    @TableField("script_version")
    private Integer scriptVersion;

    @TableField("is_active")
    private Boolean isActive;

    private String voice;
    private String ttsModel;

    @TableField("tts_params")
    private String ttsParams;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("created_by")
    private Long createdBy;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public SlideHtmlSegmentScript() {}

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