package com.microcourse.dto.hermes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class HermesCourseListVO {

    private String hermesCourseId;
    private Long courseId;
    private String title;
    private Integer status;
    private String statusText;
    private Long categoryId;
    private String categoryName;
    private String courseType;
    private LocalDateTime lastSyncAt;
    private LocalDateTime createdAt;

    public HermesCourseListVO() {}

    public HermesCourseListVO(String hermesCourseId, Long courseId, String title,
                              Integer status, String statusText, Long categoryId,
                              String categoryName, String courseType,
                              LocalDateTime lastSyncAt, LocalDateTime createdAt) {
        this.hermesCourseId = hermesCourseId;
        this.courseId = courseId;
        this.title = title;
        this.status = status;
        this.statusText = statusText;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.courseType = courseType;
        this.lastSyncAt = lastSyncAt;
        this.createdAt = createdAt;
    }

    public String getHermesCourseId() { return hermesCourseId; }
    public void setHermesCourseId(String hermesCourseId) { this.hermesCourseId = hermesCourseId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getStatusText() { return statusText; }
    public void setStatusText(String statusText) { this.statusText = statusText; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getCourseType() { return courseType; }
    public void setCourseType(String courseType) { this.courseType = courseType; }
    public LocalDateTime getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(LocalDateTime lastSyncAt) { this.lastSyncAt = lastSyncAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}