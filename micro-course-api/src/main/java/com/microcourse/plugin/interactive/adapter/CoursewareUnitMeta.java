package com.microcourse.plugin.interactive.adapter;

/**
 * 课件单元元数据 (PPT page / HTML unit 通用抽象)
 */
public interface CoursewareUnitMeta {
    Long getId();
    Long getSectionId();
    Long getCourseId();
    String getTitle();
}