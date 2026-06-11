package com.microcourse.dto;

import java.time.LocalDateTime;
import java.util.List;

public class CourseCategoryVO {

    private Long id;
    private String name;
    private Long parentId;
    private Integer level;
    private Integer sortOrder;
    private List<CourseCategoryVO> children;
    private LocalDateTime createdAt;

    public CourseCategoryVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public List<CourseCategoryVO> getChildren() { return children; }
    public void setChildren(List<CourseCategoryVO> children) { this.children = children; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}