package com.microcourse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("micro_specialty_courses")
public class MicroSpecialtyCourse {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long microSpecialtyId;
    private Long courseId;
    private Integer sortOrder;
    private Boolean isRequired;
    private BigDecimal credits;
    private Integer hours;
    private BigDecimal minScore;
    private String recommendedSemester;
    private LocalDateTime createdAt;

    public MicroSpecialtyCourse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMicroSpecialtyId() { return microSpecialtyId; }
    public void setMicroSpecialtyId(Long microSpecialtyId) { this.microSpecialtyId = microSpecialtyId; }
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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
