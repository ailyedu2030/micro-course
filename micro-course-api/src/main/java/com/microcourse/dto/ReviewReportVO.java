package com.microcourse.dto;

import java.time.LocalDateTime;

/**
 * 举报处理视图对象
 */
public class ReviewReportVO {

    private Long id;
    private Long reporterId;
    private String reporterName;
    private String reportedItemType;
    private Long reportedItemId;
    private String reason;
    private Integer status;
    private String statusText;
    private Long reviewerId;
    private String reviewerName;
    private String reviewNotes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ReviewReportVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getReporterId() { return reporterId; }
    public void setReporterId(Long reporterId) { this.reporterId = reporterId; }
    public String getReporterName() { return reporterName; }
    public void setReporterName(String reporterName) { this.reporterName = reporterName; }
    public String getReportedItemType() { return reportedItemType; }
    public void setReportedItemType(String reportedItemType) { this.reportedItemType = reportedItemType; }
    public Long getReportedItemId() { return reportedItemId; }
    public void setReportedItemId(Long reportedItemId) { this.reportedItemId = reportedItemId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getStatusText() { return statusText; }
    public void setStatusText(String statusText) { this.statusText = statusText; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public String getReviewerName() { return reviewerName; }
    public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }
    public String getReviewNotes() { return reviewNotes; }
    public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
