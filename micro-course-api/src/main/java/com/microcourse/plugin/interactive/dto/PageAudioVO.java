package com.microcourse.plugin.interactive.dto;

/**
 * 播放器音频节点 VO（P0 聚合契约，方案 §5.1）。
 * PPT 页 / HTML 段共用的"可播放音频"描述：
 * url 为可流式 token URL（/api/courses/{cid}/courseware/audio/{token}），
 * 浏览器 <audio> 可直接加载（token 即能力凭证，无需 Authorization 头）。
 */
public class PageAudioVO {

    private String url;
    private String token;
    private Integer durationMs;
    private String status;
    private String voiceUsed;
    private String modelUsed;
    private Long scriptId;

    public PageAudioVO() {}

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getVoiceUsed() { return voiceUsed; }
    public void setVoiceUsed(String voiceUsed) { this.voiceUsed = voiceUsed; }
    public String getModelUsed() { return modelUsed; }
    public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }
    public Long getScriptId() { return scriptId; }
    public void setScriptId(Long scriptId) { this.scriptId = scriptId; }
}
