package com.microcourse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class DepartmentCreateRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String code;

    private Long parentId;

    @NotNull(message = "排序不能为空")
    private Integer sortOrder;

    public DepartmentCreateRequest() {}

    public DepartmentCreateRequest(String name, String code, Long parentId, Integer sortOrder) {
        this.name = name;
        this.code = code;
        this.parentId = parentId;
        this.sortOrder = sortOrder;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}