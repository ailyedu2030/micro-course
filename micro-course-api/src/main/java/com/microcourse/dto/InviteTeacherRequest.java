package com.microcourse.dto;

import jakarta.validation.constraints.NotNull;

public class InviteTeacherRequest {
    @NotNull(message = "教师ID不能为空")
    private Long teacherId;
    private String role;
    private String message;
    /** 重邀时可选指定课程（缺省复用原记录课程） */
    private Long courseId;

    public InviteTeacherRequest() {}
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
}
