package com.microcourse.dto.storage;

import java.time.LocalDateTime;

public class StorageApplicationSummaryVO {

    private Long id;
    private String title;
    private String microSpecialtyName;
    private String type;
    private String status;
    private String departmentName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public StorageApplicationSummaryVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMicroSpecialtyName() { return microSpecialtyName; }
    public void setMicroSpecialtyName(String microSpecialtyName) { this.microSpecialtyName = microSpecialtyName; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
