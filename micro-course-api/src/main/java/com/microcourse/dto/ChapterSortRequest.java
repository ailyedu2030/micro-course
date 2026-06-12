package com.microcourse.dto;

import jakarta.validation.constraints.NotNull;

public class ChapterSortRequest {

    @NotNull(message = "章节ID不能为空")
    private Long id;

    @NotNull(message = "排序顺序不能为空")
    private Integer sortOrder;

    public ChapterSortRequest() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}