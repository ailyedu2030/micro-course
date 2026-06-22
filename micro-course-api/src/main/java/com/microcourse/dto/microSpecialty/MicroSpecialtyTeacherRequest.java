package com.microcourse.dto.microSpecialty;
import jakarta.validation.constraints.NotNull;
public class MicroSpecialtyTeacherRequest {
    @NotNull(message = "教师ID不能为空") private Long teacherId;
    private String role; private Long courseId; private String responsibility;
    public Long getTeacherId() { return teacherId; } public void setTeacherId(Long v) { teacherId = v; }
    public String getRole() { return role; } public void setRole(String v) { role = v; }
    public Long getCourseId() { return courseId; } public void setCourseId(Long v) { courseId = v; }
    public String getResponsibility() { return responsibility; } public void setResponsibility(String v) { responsibility = v; }
}
