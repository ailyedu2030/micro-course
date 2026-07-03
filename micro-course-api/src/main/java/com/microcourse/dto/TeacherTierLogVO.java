package com.microcourse.dto;

import java.time.LocalDateTime;

/**
 * 教师等级变更记录 VO
 */
public class TeacherTierLogVO {

    private String fromTier;
    private String fromTierLabel;
    private String toTier;
    private String toTierLabel;
    private String reason;
    private String triggeredBy;
    private LocalDateTime createdAt;

    public TeacherTierLogVO() {}

    public String getFromTier() { return fromTier; }
    public void setFromTier(String fromTier) { this.fromTier = fromTier; }
    public String getFromTierLabel() { return fromTierLabel; }
    public void setFromTierLabel(String fromTierLabel) { this.fromTierLabel = fromTierLabel; }
    public String getToTier() { return toTier; }
    public void setToTier(String toTier) { this.toTier = toTier; }
    public String getToTierLabel() { return toTierLabel; }
    public void setToTierLabel(String toTierLabel) { this.toTierLabel = toTierLabel; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(String triggeredBy) { this.triggeredBy = triggeredBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
