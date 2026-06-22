package com.microcourse.dto.microSpecialty;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MicroSpecialtyStatsVO {
    private Long totalEnrollments; private Long completedCount; private Long inProgressCount;
    private Long failedCount; private BigDecimal averageScore; private BigDecimal enrollmentRate;
    private BigDecimal completionRate; private BigDecimal qualityScore;
    public MicroSpecialtyStatsVO() {}
    public Long getTotalEnrollments() { return totalEnrollments; } public void setTotalEnrollments(Long v) { totalEnrollments = v; }
    public Long getCompletedCount() { return completedCount; } public void setCompletedCount(Long v) { completedCount = v; }
    public Long getInProgressCount() { return inProgressCount; } public void setInProgressCount(Long v) { inProgressCount = v; }
    public Long getFailedCount() { return failedCount; } public void setFailedCount(Long v) { failedCount = v; }
    public BigDecimal getAverageScore() { return averageScore; } public void setAverageScore(BigDecimal v) { averageScore = v; }
    public BigDecimal getEnrollmentRate() { return enrollmentRate; } public void setEnrollmentRate(BigDecimal v) { enrollmentRate = v; }
    public BigDecimal getCompletionRate() { return completionRate; } public void setCompletionRate(BigDecimal v) { completionRate = v; }
    public BigDecimal getQualityScore() { return qualityScore; } public void setQualityScore(BigDecimal v) { qualityScore = v; }
}
