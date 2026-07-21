package com.microcourse.event.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * ChapterEventPayload. P1 spec §四.4.1 + §六.6.1.
 * 手写 getter/setter.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChapterEventPayload {
    private Long id;
    private String title;
    private Integer sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
