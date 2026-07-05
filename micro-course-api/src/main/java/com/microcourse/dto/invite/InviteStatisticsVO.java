package com.microcourse.dto.invite;

public class InviteStatisticsVO {

    private long totalInvited;
    private long activeCount;
    private long declinedCount;
    private long pendingAcademicCount;
    private long removedCount;
    private double averageAcceptanceTimeHours;
    private long expiryCount;
    private long pendingCount;

    public InviteStatisticsVO() {}

    public long getTotalInvited() { return totalInvited; }
    public void setTotalInvited(long totalInvited) { this.totalInvited = totalInvited; }

    public long getActiveCount() { return activeCount; }
    public void setActiveCount(long activeCount) { this.activeCount = activeCount; }

    public long getDeclinedCount() { return declinedCount; }
    public void setDeclinedCount(long declinedCount) { this.declinedCount = declinedCount; }

    public long getPendingAcademicCount() { return pendingAcademicCount; }
    public void setPendingAcademicCount(long pendingAcademicCount) { this.pendingAcademicCount = pendingAcademicCount; }

    public long getRemovedCount() { return removedCount; }
    public void setRemovedCount(long removedCount) { this.removedCount = removedCount; }

    public double getAverageAcceptanceTimeHours() { return averageAcceptanceTimeHours; }
    public void setAverageAcceptanceTimeHours(double averageAcceptanceTimeHours) { this.averageAcceptanceTimeHours = averageAcceptanceTimeHours; }

    public long getExpiryCount() { return expiryCount; }
    public void setExpiryCount(long expiryCount) { this.expiryCount = expiryCount; }

    public long getPendingCount() { return pendingCount; }
    public void setPendingCount(long pendingCount) { this.pendingCount = pendingCount; }
}
