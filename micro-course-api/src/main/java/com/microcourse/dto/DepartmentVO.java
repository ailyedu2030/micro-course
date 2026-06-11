package com.microcourse.dto;

import java.time.LocalDateTime;
import java.util.List;

public class DepartmentVO {

    private Long id;
    private String name;
    private String code;
    private Long parentId;
    private String parentName;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private List<DepartmentVO> children;

    public DepartmentVO() {}

    public DepartmentVO(Long id, String name, String code, Long parentId, String parentName,
                       Integer sortOrder, LocalDateTime createdAt, List<DepartmentVO> children) {
        this.id = id;
        this.name = name;
        this.code = code;
        this.parentId = parentId;
        this.parentName = parentName;
        this.sortOrder = sortOrder;
        this.createdAt = createdAt;
        this.children = children;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getParentName() { return parentName; }
    public void setParentName(String parentName) { this.parentName = parentName; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public List<DepartmentVO> getChildren() { return children; }
    public void setChildren(List<DepartmentVO> children) { this.children = children; }
}