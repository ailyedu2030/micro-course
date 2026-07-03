package com.microcourse.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class EnrollmentVO {

    private Long id;
    private Long courseId;
    private String courseName;
    /** @deprecated 语义同 courseName，前端迁移后删除。请使用 courseName。 */
    @Deprecated
    private String courseTitle;
    private String teacherName;
    private String coverUrl;
    private Long userId;
    /** @deprecated 语义同 realName，前端迁移后删除。请使用 realName。 */
    @Deprecated
    private String userName;
    private Double progress;
    private Boolean completed;
    private BigDecimal finalScore;
    private String finalGrade;
    private String enrollmentStatus;
    private String sourceChannel;
    private Long bundleId;
    private LocalDateTime enrolledAt;
    private LocalDateTime completedAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastWatchAt;

    /** P0-3: 学员管理表格需要的用户维度字段 */
    private String username;    // 学号（users.username）
    private String realName;    // 姓名
    private String className;   // 班级名称（关联 classes 表）
    private String majorName;   // 专业名称（关联 majors 表）

    public EnrollmentVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public String getCourseTitle() { return courseTitle; }
    public void setCourseTitle(String courseTitle) { this.courseTitle = courseTitle; }
    public String getTeacherName() { return teacherName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
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
    public String getSourceChannel() { return sourceChannel; }
    public void setSourceChannel(String sourceChannel) { this.sourceChannel = sourceChannel; }
    public Long getBundleId() { return bundleId; }
    public void setBundleId(Long bundleId) { this.bundleId = bundleId; }
    public LocalDateTime getEnrolledAt() { return enrolledAt; }
    public void setEnrolledAt(LocalDateTime enrolledAt) { this.enrolledAt = enrolledAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getLastWatchAt() { return lastWatchAt; }
    public void setLastWatchAt(LocalDateTime lastWatchAt) { this.lastWatchAt = lastWatchAt; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public String getMajorName() { return majorName; }
    public void setMajorName(String majorName) { this.majorName = majorName; }
}
