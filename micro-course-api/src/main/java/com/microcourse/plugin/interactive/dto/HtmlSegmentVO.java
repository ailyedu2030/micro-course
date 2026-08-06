package com.microcourse.plugin.interactive.dto;

/**
 * HTML 课件分段 VO（P0 聚合契约，方案 §5.1）。
 * 与 HTML DOM 锚点（segment_marker / data-segment）一一对应，
 * 播放器据此做"时间线 ↔ 元素"映射与 active 高亮。
 */
public class HtmlSegmentVO {

    private Integer index;
    private String marker;
    private String selector;
    private String text;
    private String scriptText;
    private Boolean interactive;
    private PageAudioVO audio;

    public HtmlSegmentVO() {}

    public Integer getIndex() { return index; }
    public void setIndex(Integer index) { this.index = index; }
    public String getMarker() { return marker; }
    public void setMarker(String marker) { this.marker = marker; }
    public String getSelector() { return selector; }
    public void setSelector(String selector) { this.selector = selector; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getScriptText() { return scriptText; }
    public void setScriptText(String scriptText) { this.scriptText = scriptText; }
    public Boolean getInteractive() { return interactive; }
    public void setInteractive(Boolean interactive) { this.interactive = interactive; }
    public PageAudioVO getAudio() { return audio; }
    public void setAudio(PageAudioVO audio) { this.audio = audio; }
}
