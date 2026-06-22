package com.microcourse.dto.microSpecialty;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MicroSpecialtyCourseVO {
    private Long id; private Long microSpecialtyId; private Long courseId;
    private String courseTitle; private String courseType; private String teacherName;
    private Integer sortOrder; private Boolean isRequired; private BigDecimal credits;
    private Integer hours; private BigDecimal minScore; private String recommendedSemester;
    public MicroSpecialtyCourseVO() {}
    public Long getId() { return id; } public void setId(Long v) { id = v; }
    public Long getMicroSpecialtyId() { return microSpecialtyId; } public void setMicroSpecialtyId(Long v) { microSpecialtyId = v; }
    public Long getCourseId() { return courseId; } public void setCourseId(Long v) { courseId = v; }
    public String getCourseTitle() { return courseTitle; } public void setCourseTitle(String v) { courseTitle = v; }
    public String getCourseType() { return courseType; } public void setCourseType(String v) { courseType = v; }
    public String getTeacherName() { return teacherName; } public void setTeacherName(String v) { teacherName = v; }
    public Integer getSortOrder() { return sortOrder; } public void setSortOrder(Integer v) { sortOrder = v; }
    public Boolean getIsRequired() { return isRequired; } public void setIsRequired(Boolean v) { isRequired = v; }
    public BigDecimal getCredits() { return credits; } public void setCredits(BigDecimal v) { credits = v; }
    public Integer getHours() { return hours; } public void setHours(Integer v) { hours = v; }
    public BigDecimal getMinScore() { return minScore; } public void setMinScore(BigDecimal v) { minScore = v; }
    public String getRecommendedSemester() { return recommendedSemester; } public void setRecommendedSemester(String v) { recommendedSemester = v; }
}
