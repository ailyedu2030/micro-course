package com.microcourse.plugin.interactive.adapter;

/**
 * 课件分段元数据 (PPT page 或 HTML segment)
 */
public interface CoursewareSegmentMeta {
    Long getSegmentId();
    Integer getSegmentIndex();
    String getTitle();
    String getStatus();
}