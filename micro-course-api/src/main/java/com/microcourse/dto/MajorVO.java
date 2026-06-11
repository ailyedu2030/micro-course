package com.microcourse.dto;

import java.time.LocalDateTime;

public class MajorVO {

    private Long id;
    private String name;
    private String code;
    private Long departmentId;
    private String departmentName;
    private Integer sortOrder;
    private LocalDateTime createdAt;

    public MajorVO() {}

    public MajorVO(Long id, String name, String code, Long departmentId, String departmentName,
                   Integer sortOrder, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.code = code;
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.sortOrder = sortOrder;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}