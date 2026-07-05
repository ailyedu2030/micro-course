package com.microcourse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("micro_specialty_enrollments")
public class MicroSpecialtyEnrollment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long microSpecialtyId;
    private Long userId;
    private String source;
    private Long classId;
    private String status;
    /** P1-I-14: INTEGER 备用列，后续迭代切换为主字段 */
    @TableField(exist = false)
    private Integer statusCode;
    private BigDecimal progress;
    private BigDecimal creditsEarned;
    private Integer coursesCompleted;
    private Integer coursesRequired;
    private BigDecimal finalScore;
    private String finalGrade;
    private Long certificateId;
    /** G2: 班级导入时，前置课未通过/容量满/时间冲突/已存在等不能 enroll 的课程。结构 [{courseId, courseName, reason}]，JSON 字符串 */
    @TableField(value = "pending_courses", typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private String pendingCourses;  // JSON 字符串
    private LocalDateTime appliedAt;
    private LocalDateTime approvedAt;
    private Long approvedBy;
    private LocalDateTime completedAt;
    private LocalDateTime droppedAt;
    private String dropReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Version
    private Integer version;
    /** 报名失败原因（仅前端展示，不入库） */
    @TableField(exist = false)
    private String failReason;

    public MicroSpecialtyEnrollment() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMicroSpecialtyId() { return microSpecialtyId; }
    public void setMicroSpecialtyId(Long microSpecialtyId) { this.microSpecialtyId = microSpecialtyId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getStatusCode() { return statusCode; }
    public void setStatusCode(Integer statusCode) { this.statusCode = statusCode; }
    public BigDecimal getProgress() { return progress; }
    public void setProgress(BigDecimal progress) { this.progress = progress; }
    public BigDecimal getCreditsEarned() { return creditsEarned; }
    public void setCreditsEarned(BigDecimal creditsEarned) { this.creditsEarned = creditsEarned; }
    public Integer getCoursesCompleted() { return coursesCompleted; }
    public void setCoursesCompleted(Integer coursesCompleted) { this.coursesCompleted = coursesCompleted; }
    public Integer getCoursesRequired() { return coursesRequired; }
    public void setCoursesRequired(Integer coursesRequired) { this.coursesRequired = coursesRequired; }
    public BigDecimal getFinalScore() { return finalScore; }
    public void setFinalScore(BigDecimal finalScore) { this.finalScore = finalScore; }
    public String getFinalGrade() { return finalGrade; }
    public void setFinalGrade(String finalGrade) { this.finalGrade = finalGrade; }
    public Long getCertificateId() { return certificateId; }
    public void setCertificateId(Long certificateId) { this.certificateId = certificateId; }
    public String getPendingCourses() { return pendingCourses; }
    public void setPendingCourses(String pendingCourses) { this.pendingCourses = pendingCourses; }
    public LocalDateTime getAppliedAt() { return appliedAt; }
    public void setAppliedAt(LocalDateTime appliedAt) { this.appliedAt = appliedAt; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public Long getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Long approvedBy) { this.approvedBy = approvedBy; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getDroppedAt() { return droppedAt; }
    public void setDroppedAt(LocalDateTime droppedAt) { this.droppedAt = droppedAt; }
    public String getDropReason() { return dropReason; }
    public void setDropReason(String dropReason) { this.dropReason = dropReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }
}
