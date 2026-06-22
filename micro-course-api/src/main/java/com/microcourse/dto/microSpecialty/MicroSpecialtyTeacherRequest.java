package com.microcourse.dto.microSpecialty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class MicroSpecialtyTeacherRequest {

    @NotNull(message = "教师ID不能为空")
    private Long teacherId;

    @NotBlank(message = "角色不能为空")
    private String role;

    private Long courseId;
    private String responsibility;

    public MicroSpecialtyTeacherRequest() {}

    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }

    public String getResponsibility() { return responsibility; }
    public void setResponsibility(String responsibility) { this.responsibility = responsibility; }
}
