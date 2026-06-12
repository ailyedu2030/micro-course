package com.microcourse.dto;

import java.time.LocalDateTime;

public class TeachingClassStudentVO {

    private Long id;
    private Long classId;
    private Long userId;
    private LocalDateTime enrolledAt;
    private String status;
    private String realName;
    private String studentNo;
    private String avatar;
    private String className;

    public TeachingClassStudentVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LocalDateTime getEnrolledAt() { return enrolledAt; }
    public void setEnrolledAt(LocalDateTime enrolledAt) { this.enrolledAt = enrolledAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getStudentNo() { return studentNo; }
    public void setStudentNo(String studentNo) { this.studentNo = studentNo; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
}