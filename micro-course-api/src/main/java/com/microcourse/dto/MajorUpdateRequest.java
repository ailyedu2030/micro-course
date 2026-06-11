package com.microcourse.dto;

public class MajorUpdateRequest {

    private String name;
    private String code;
    private Long departmentId;
    private Integer sortOrder;

    public MajorUpdateRequest() {}

    public MajorUpdateRequest(String name, String code, Long departmentId, Integer sortOrder) {
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