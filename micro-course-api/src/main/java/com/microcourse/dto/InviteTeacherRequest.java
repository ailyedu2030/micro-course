package com.microcourse.dto;

import jakarta.validation.constraints.NotNull;

public class InviteTeacherRequest {
    @NotNull(message = "教师ID不能为空")
    private Long teacherId;
    private String role;
    private String message;

    public InviteTeacherRequest() {}
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
