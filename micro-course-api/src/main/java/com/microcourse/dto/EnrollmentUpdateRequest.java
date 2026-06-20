package com.microcourse.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

public class EnrollmentUpdateRequest {

    @Min(value = 0, message = "进度不能小于0")
    @Max(value = 100, message = "进度不能大于100")
    private Double progress;

    private Boolean completed;

    @DecimalMin(value = "0.0", message = "分数不能小于0")
    @DecimalMax(value = "100.0", message = "分数不能大于100")
    private BigDecimal finalScore;

    private String finalGrade;

    private String enrollmentStatus;

    public EnrollmentUpdateRequest() {}

    public Double getProgress() { return progress; }
    public void setProgress(Double progress) { this.progress = progress; }
    public Boolean getCompleted() { return completed; }
    public void setCompleted(Boolean completed) { this.completed = completed; }
    public BigDecimal getFinalScore() { return finalScore; }
    public void setFinalScore(BigDecimal finalScore) { this.finalScore = finalScore; }
    public String getFinalGrade() { return finalGrade; }
    public void setFinalGrade(String finalGrade) { this.finalGrade = finalGrade; }
    public String getEnrollmentStatus() { return enrollmentStatus; }
    public void setEnrollmentStatus(String enrollmentStatus) { this.enrollmentStatus = enrollmentStatus; }
}
