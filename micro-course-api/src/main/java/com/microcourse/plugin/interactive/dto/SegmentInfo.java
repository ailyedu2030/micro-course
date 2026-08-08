package com.microcourse.plugin.interactive.dto;

/**
 * HTML 课件自动分段检测结果条目（P2-1，方案 §6.2）。
 * 与 {@code HtmlSegmentVO} 同构（index/marker/selector/text），
 * 供检测端点返回给前端展示/落库 segment_marker。
 */
public class SegmentInfo {

    /** 1 基段序号（1, 2, 3 ...） */
    private Integer index;
    /** 段标记（如 seg-1, seg-2），可作 DOM id 锚点 */
    private String marker;
    /** CSS 选择器（如 #seg-1） */
    private String selector;
    /** 段文本摘要（首 100 字符，用于前端预览） */
    private String text;

    public SegmentInfo() {}

    public SegmentInfo(Integer index, String marker, String selector, String text) {
        this.index = index;
        this.marker = marker;
        this.selector = selector;
        this.text = text;
    }

    public Integer getIndex() { return index; }
    public void setIndex(Integer index) { this.index = index; }
    public String getMarker() { return marker; }
    public void setMarker(String marker) { this.marker = marker; }
    public String getSelector() { return selector; }
    public void setSelector(String selector) { this.selector = selector; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
