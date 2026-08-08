package com.microcourse.plugin.interactive.dto;

import java.util.List;

/**
 * 段检测端点响应（POST /api/courses/{cid}/html/units/{unitId}/detect）。
 * detectedCount 已落库 slide_html_units.detected_segments；
 * segments 为本次检测出的段列表（含 marker/selector/text，供前端即时展示）。
 */
public class SegmentDetectionResult {

    /** 检测到的段落总数（1-50，0 表示无可检测内容） */
    private int detectedCount;
    private List<SegmentInfo> segments;

    public SegmentDetectionResult() {}

    public SegmentDetectionResult(int detectedCount, List<SegmentInfo> segments) {
        this.detectedCount = detectedCount;
        this.segments = segments;
    }

    public int getDetectedCount() { return detectedCount; }
    public void setDetectedCount(int detectedCount) { this.detectedCount = detectedCount; }
    public List<SegmentInfo> getSegments() { return segments; }
    public void setSegments(List<SegmentInfo> segments) { this.segments = segments; }
}
