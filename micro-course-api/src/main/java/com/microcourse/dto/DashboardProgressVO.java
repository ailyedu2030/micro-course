package com.microcourse.dto;

import java.math.BigDecimal;

public class DashboardProgressVO {

    private Long totalStudents;
    private Long activeStudents;
    private BigDecimal avgProgress;
    private Long completedStudents;
    private BigDecimal completionRate;
    private Long totalLearningMinutes;

    public DashboardProgressVO() {}

    public Long getTotalStudents() { return totalStudents; }
    public void setTotalStudents(Long totalStudents) { this.totalStudents = totalStudents; }
    public Long getActiveStudents() { return activeStudents; }
    public void setActiveStudents(Long activeStudents) { this.activeStudents = activeStudents; }
    public BigDecimal getAvgProgress() { return avgProgress; }
    public void setAvgProgress(BigDecimal avgProgress) { this.avgProgress = avgProgress; }
    public Long getCompletedStudents() { return completedStudents; }
    public void setCompletedStudents(Long completedStudents) { this.completedStudents = completedStudents; }
    public BigDecimal getCompletionRate() { return completionRate; }
    public void setCompletionRate(BigDecimal completionRate) { this.completionRate = completionRate; }
    public Long getTotalLearningMinutes() { return totalLearningMinutes; }
    public void setTotalLearningMinutes(Long totalLearningMinutes) { this.totalLearningMinutes = totalLearningMinutes; }
}