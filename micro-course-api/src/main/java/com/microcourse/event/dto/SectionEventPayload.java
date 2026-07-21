package com.microcourse.event.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * SectionEventPayload. P1 spec §四.4.1 + §六.6.1.
 *
 * NOTE: HermesCourseDetailVO 没有 section 层 (见 spec §一.1.2 架构差异),
 * 所以此 payload 用于 OutboxPoller 内部传播 + HermesEventController 反规范化 (P3 再处理).
 *
 * 手写 getter/setter.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SectionEventPayload {
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
