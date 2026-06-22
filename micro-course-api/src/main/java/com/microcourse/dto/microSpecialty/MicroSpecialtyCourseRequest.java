package com.microcourse.dto.microSpecialty;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class MicroSpecialtyCourseRequest {

    @NotNull(message = "课程ID不能为空")
    private Long courseId;

    private Integer sortOrder;
    private Boolean isRequired;
    private BigDecimal credits;
    private Integer hours;
    private BigDecimal minScore;
    private String recommendedSemester;

    public MicroSpecialtyCourseRequest() {}

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public Boolean getIsRequired() { return isRequired; }
    public void setIsRequired(Boolean isRequired) { this.isRequired = isRequired; }

    public BigDecimal getCredits() { return credits; }
    public void setCredits(BigDecimal credits) { this.credits = credits; }

    public Integer getHours() { return hours; }
    public void setHours(Integer hours) { this.hours = hours; }

    public BigDecimal getMinScore() { return minScore; }
    public void setMinScore(BigDecimal minScore) { this.minScore = minScore; }

    public String getRecommendedSemester() { return recommendedSemester; }
    public void setRecommendedSemester(String recommendedSemester) { this.recommendedSemester = recommendedSemester; }
}
