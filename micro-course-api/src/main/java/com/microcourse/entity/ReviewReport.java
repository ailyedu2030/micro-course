package com.microcourse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 举报处理实体
 */
@TableName("review_reports")
public class ReviewReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("reporter_id")
    private Long reporterId;

    @TableField("reported_item_type")
    private String reportedItemType;

    @TableField("reported_item_id")
    private Long reportedItemId;

    private String reason;

    /** 0=待处理 1=已驳回(内容保留) 2=已处理(内容已删除) */
    private Integer status;

    @TableField("reviewer_id")
    private Long reviewerId;

    @TableField("review_notes")
    private String reviewNotes;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public ReviewReport() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getReporterId() { return reporterId; }
    public void setReporterId(Long reporterId) { this.reporterId = reporterId; }
    public String getReportedItemType() { return reportedItemType; }
    public void setReportedItemType(String reportedItemType) { this.reportedItemType = reportedItemType; }
    public Long getReportedItemId() { return reportedItemId; }
    public void setReportedItemId(Long reportedItemId) { this.reportedItemId = reportedItemId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public String getReviewNotes() { return reviewNotes; }
    public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
