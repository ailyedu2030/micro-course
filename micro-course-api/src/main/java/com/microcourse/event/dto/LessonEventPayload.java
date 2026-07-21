package com.microcourse.event.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * LessonEventPayload. P1 spec §四.4.1 + §六.6.1.
 * 手写 getter/setter + 链式 fluent API.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LessonEventPayload {
    private Long id;
    private String title;
    private String lessonType;
    private String contentUrl;
    private Integer sortOrder;

    public Long getId() { return id; }
    public LessonEventPayload setId(Long id) { this.id = id; return this; }
    public String getTitle() { return title; }
    public LessonEventPayload setTitle(String title) { this.title = title; return this; }
    public String getLessonType() { return lessonType; }
    public LessonEventPayload setLessonType(String lessonType) { this.lessonType = lessonType; return this; }
    public String getContentUrl() { return contentUrl; }
    public LessonEventPayload setContentUrl(String contentUrl) { this.contentUrl = contentUrl; return this; }
    public Integer getSortOrder() { return sortOrder; }
    public LessonEventPayload setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; return this; }
}
