package com.microcourse.dto;

import java.time.LocalDateTime;

public class CertificateVO {

    private Long id;
    private Long userId;
    private Long courseId;
    private String courseTitle;
    private String teacherName;
    private String certCode;
    private LocalDateTime issuedAt;

    public CertificateVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getCourseTitle() { return courseTitle; }
    public void setCourseTitle(String courseTitle) { this.courseTitle = courseTitle; }
    public String getTeacherName() { return teacherName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }
    public String getCertCode() { return certCode; }
    public void setCertCode(String certCode) { this.certCode = certCode; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDateTime issuedAt) { this.issuedAt = issuedAt; }
}