package com.microcourse.plugin.interactive.dto;

public class SegmentAudioVO {

    private Integer pageNumber;
    private String url;
    private Integer duration;
    private String status;
    private String voiceUsed;
    private Long scriptId;

    public SegmentAudioVO() {}

    public SegmentAudioVO(Integer pageNumber, String url, Integer duration) {
        this.pageNumber = pageNumber;
        this.url = url;
        this.duration = duration;
    }

    public static SegmentAudioVO fromPpt(PptAudioDTO d) {
        SegmentAudioVO vo = new SegmentAudioVO();
        vo.setUrl(d.getAudioUrl());
        vo.setDuration(d.getAudioDurationMs());
        vo.setStatus(d.getStatus());
        vo.setVoiceUsed(d.getVoiceUsed());
        vo.setScriptId(d.getScriptId());
        return vo;
    }

    public static SegmentAudioVO fromHtml(HtmlSegmentAudioDTO d) {
        SegmentAudioVO vo = new SegmentAudioVO();
        vo.setUrl(d.getAudioUrl());
        vo.setDuration(d.getAudioDurationMs());
        vo.setStatus(d.getStatus());
        vo.setVoiceUsed(d.getVoiceUsed());
        vo.setScriptId(d.getSegmentScriptId());
        return vo;
    }

    public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getVoiceUsed() { return voiceUsed; }
    public void setVoiceUsed(String voiceUsed) { this.voiceUsed = voiceUsed; }

    public Long getScriptId() { return scriptId; }
    public void setScriptId(Long scriptId) { this.scriptId = scriptId; }
}
