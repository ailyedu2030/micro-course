package com.microcourse.plugin.interactive.dto;

import java.time.LocalDateTime;

public class SlideVO {

    private Long id;
    private Long courseId;
    private String fileName;
    private Integer totalPages;
    private Integer status;
    private String statusText;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public SlideVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Integer getTotalPages() { return totalPages; }
    public void setTotalPages(Integer totalPages) { this.totalPages = totalPages; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getStatusText() { return statusText; }
    public void setStatusText(String statusText) { this.statusText = statusText; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public static String statusText(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 0: return "上传中";
            case 1: return "渲染中";
            case 2: return "就绪";
            case 3: return "失败";
            default: return "未知";
        }
    }
}
