package com.microcourse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 教师评级 (teacher_ratings 表)
 * V112 migration
 */
@TableName("teacher_ratings")
public class TeacherRating {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("teacher_id")
    private Long teacherId;

    @TableField("rating_score")
    private BigDecimal ratingScore;

    @TableField("tier")
    private String tier;

    @TableField("avg_student_rating")
    private BigDecimal avgStudentRating;

    @TableField("completion_rate")
    private BigDecimal completionRate;

    @TableField("total_students")
    private Integer totalStudents;

    @TableField("total_courses")
    private Integer totalCourses;

    @TableField("calculated_at")
    private LocalDateTime calculatedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("manual_adjustment")
    private Boolean manualAdjustment;

    public TeacherRating() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }

    public BigDecimal getRatingScore() { return ratingScore; }
    public void setRatingScore(BigDecimal ratingScore) { this.ratingScore = ratingScore; }

    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }

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

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Boolean getManualAdjustment() { return manualAdjustment; }
    public void setManualAdjustment(Boolean manualAdjustment) { this.manualAdjustment = manualAdjustment; }
}
