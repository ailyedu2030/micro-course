package com.microcourse.dto;

import java.time.LocalDateTime;

/**
 * 课程审核日志 VO
 */
public class CourseReviewLogVO {

    private Long id;
    private Long courseId;
    private Long reviewerId;
    private String reviewerName;
    private String action;
    private String reason;
    private Integer previousStatus;
    private Integer newStatus;
    private LocalDateTime createdAt;

    public CourseReviewLogVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public String getReviewerName() { return reviewerName; }
    public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Integer getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(Integer previousStatus) { this.previousStatus = previousStatus; }
    public Integer getNewStatus() { return newStatus; }
    public void setNewStatus(Integer newStatus) { this.newStatus = newStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
