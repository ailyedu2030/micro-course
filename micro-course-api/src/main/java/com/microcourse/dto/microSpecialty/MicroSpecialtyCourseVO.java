package com.microcourse.dto.microSpecialty;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MicroSpecialtyCourseVO {

    private Long id;
    private Long microSpecialtyId;
    private Long courseId;
    private String courseTitle;
    private String courseType;
    private String teacherName;
    private Integer sortOrder;
    private Boolean isRequired;
    private BigDecimal credits;
    private Integer hours;
    private BigDecimal minScore;
    private String recommendedSemester;

    public MicroSpecialtyCourseVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getMicroSpecialtyId() { return microSpecialtyId; }
    public void setMicroSpecialtyId(Long microSpecialtyId) { this.microSpecialtyId = microSpecialtyId; }

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }

    public String getCourseTitle() { return courseTitle; }
    public void setCourseTitle(String courseTitle) { this.courseTitle = courseTitle; }

    public String getCourseType() { return courseType; }
    public void setCourseType(String courseType) { this.courseType = courseType; }

    public String getTeacherName() { return teacherName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }

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
