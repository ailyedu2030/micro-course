package com.microcourse.dto.microSpecialty;
import jakarta.validation.constraints.NotNull; import java.math.BigDecimal;
public class MicroSpecialtyCourseRequest {
    @NotNull(message = "课程ID不能为空") private Long courseId;
    private Integer sortOrder; private Boolean isRequired; private BigDecimal credits;
    private Integer hours; private BigDecimal minScore; private String recommendedSemester;
    public Long getCourseId() { return courseId; } public void setCourseId(Long v) { courseId = v; }
    public Integer getSortOrder() { return sortOrder; } public void setSortOrder(Integer v) { sortOrder = v; }
    public Boolean getIsRequired() { return isRequired; } public void setIsRequired(Boolean v) { isRequired = v; }
    public BigDecimal getCredits() { return credits; } public void setCredits(BigDecimal v) { credits = v; }
    public Integer getHours() { return hours; } public void setHours(Integer v) { hours = v; }
    public BigDecimal getMinScore() { return minScore; } public void setMinScore(BigDecimal v) { minScore = v; }
    public String getRecommendedSemester() { return recommendedSemester; } public void setRecommendedSemester(String v) { recommendedSemester = v; }
}
