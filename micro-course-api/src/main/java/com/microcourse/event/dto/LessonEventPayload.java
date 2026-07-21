package com.microcourse.event.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * LessonEventPayload. P1 spec §四.4.1 + §六.6.1.
 *
 * Hermes 端已有 lessonType/contentUrl (对应 HermesCourseDetailVO.LessonVo).
 * 课件内容级同步 (PPT/HTML/Script/Audio/Flow) 在 P3 单独 spec.
 *
 * 手写 getter/setter.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LessonEventPayload {
    private Long id;
    private String title;
    private String lessonType;
    private String contentUrl;
    private Integer sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getLessonType() { return lessonType; }
    public void setLessonType(String lessonType) { this.lessonType = lessonType; }

    public String getContentUrl() { return contentUrl; }
    public void setContentUrl(String contentUrl) { this.contentUrl = contentUrl; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
