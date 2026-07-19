package com.microcourse.plugin.interactive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * PPT 课件页面讲述稿实体 (slide_ppt_page_scripts 表, V301).
 * 1 PPT page : N 脚本 (active 标记最新).
 */
@TableName("slide_ppt_page_scripts")
public class SlidePptPageScript {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("ppt_page_id")
    private Long pptPageId;

    @TableField("script_text")
    private String scriptText;

    @TableField("script_version")
    private Integer scriptVersion;

    @TableField("is_active")
    private Boolean isActive;

    private String voice;
    private String ttsModel;

    @TableField("tts_params")
    private String ttsParams;  // JSONB stored as String

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("created_by")
    private Long createdBy;

    @Version
    private Integer version;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public SlidePptPageScript() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPptPageId() { return pptPageId; }
    public void setPptPageId(Long pptPageId) { this.pptPageId = pptPageId; }
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
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}