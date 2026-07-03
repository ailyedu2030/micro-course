package com.microcourse.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 教师评级视图对象
 */
public class TeacherRatingVO {

    private Long teacherId;
    private String teacherName;
    private String teacherAvatar;
    private BigDecimal ratingScore;
    private String tier;
    private String tierLabel;
    private BigDecimal avgStudentRating;
    private BigDecimal completionRate;
    private Integer totalStudents;
    private Integer totalCourses;
    private LocalDateTime calculatedAt;
    private java.math.BigDecimal tierRate;        // 该教师当前等级的平台分账率(%)
    private java.math.BigDecimal teacherRate;      // 该教师当前等级的实际收入占比(%)

    public TeacherRatingVO() {}

    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }

    public String getTeacherName() { return teacherName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }

    public String getTeacherAvatar() { return teacherAvatar; }
    public void setTeacherAvatar(String teacherAvatar) { this.teacherAvatar = teacherAvatar; }

    public BigDecimal getRatingScore() { return ratingScore; }
    public void setRatingScore(BigDecimal ratingScore) { this.ratingScore = ratingScore; }

    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }

    public String getTierLabel() { return tierLabel; }
    public void setTierLabel(String tierLabel) { this.tierLabel = tierLabel; }

    public BigDecimal getAvgStudentRating() { return avgStudentRating; }
    public void setAvgStudentRating(BigDecimal avgStudentRating) { this.avgStudentRating = avgStudentRating; }

    public BigDecimal getCompletionRate() { return completionRate; }
    public void setCompletionRate(BigDecimal completionRate) { this.completionRate = completionRate; }

    public Integer getTotalStudents() { return totalStudents; }
    public void setTotalStudents(Integer totalStudents) { this.totalStudents = totalStudents; }

    public Integer getTotalCourses() { return totalCourses; }
    public void setTotalCourses(Integer totalCourses) { this.totalCourses = totalCourses; }

    public LocalDateTime getCalculatedAt() { return calculatedAt; }
    public void setCalculatedAt(LocalDateTime calculatedAt) { this.calculatedAt = calculatedAt; }

    public java.math.BigDecimal getTierRate() { return tierRate; }
    public void setTierRate(java.math.BigDecimal tierRate) { this.tierRate = tierRate; }

    public java.math.BigDecimal getTeacherRate() { return teacherRate; }
    public void setTeacherRate(java.math.BigDecimal teacherRate) { this.teacherRate = teacherRate; }
}
