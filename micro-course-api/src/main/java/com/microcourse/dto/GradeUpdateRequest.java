package com.microcourse.dto;

import java.math.BigDecimal;

public class GradeUpdateRequest {

    private BigDecimal score;
    private BigDecimal totalScore;
    private Boolean passed;
    private Integer duration;

    public GradeUpdateRequest() {}

    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public BigDecimal getTotalScore() { return totalScore; }
    public void setTotalScore(BigDecimal totalScore) { this.totalScore = totalScore; }
    public Boolean getPassed() { return passed; }
    public void setPassed(Boolean passed) { this.passed = passed; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
}