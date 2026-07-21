package com.microcourse.event.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * SectionEventPayload. P1 spec §四.4.1 + §六.6.1.
 * 手写 getter/setter + 链式 fluent API.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SectionEventPayload {
    private Long id;
    private String title;
    private Integer sortOrder;

    public Long getId() { return id; }
    public SectionEventPayload setId(Long id) { this.id = id; return this; }
    public String getTitle() { return title; }
    public SectionEventPayload setTitle(String title) { this.title = title; return this; }
    public Integer getSortOrder() { return sortOrder; }
    public SectionEventPayload setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; return this; }
}
