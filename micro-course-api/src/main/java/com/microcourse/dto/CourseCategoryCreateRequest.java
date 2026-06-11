package com.microcourse.dto;

import jakarta.validation.constraints.NotBlank;

public class CourseCategoryCreateRequest {

    @NotBlank
    private String name;

    private Long parentId;

    private Integer level;

    private Integer sortOrder;

    public CourseCategoryCreateRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}