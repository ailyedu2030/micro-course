package com.microcourse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class MajorCreateRequest {

    @NotBlank(message = "专业名称不能为空")
    private String name;

    @NotBlank(message = "专业代码不能为空")
    private String code;

    @NotNull(message = "院系ID不能为空")
    private Long departmentId;

    private Integer sortOrder;

    public MajorCreateRequest() {}

    public MajorCreateRequest(String name, String code, Long departmentId, Integer sortOrder) {
        this.name = name;
        this.code = code;
        this.departmentId = departmentId;
        this.sortOrder = sortOrder;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}