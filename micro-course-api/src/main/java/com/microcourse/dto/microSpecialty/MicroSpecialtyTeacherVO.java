package com.microcourse.dto.microSpecialty;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MicroSpecialtyTeacherVO {
    private Long id; private Long microSpecialtyId; private Long teacherId;
    private String teacherName; private String teacherAvatar; private String role;
    private Long courseId; private String courseTitle; private String responsibility;
    private String inviteStatus; private LocalDateTime inviteExpiresAt;
    public MicroSpecialtyTeacherVO() {}
    public Long getId() { return id; } public void setId(Long v) { id = v; }
    public Long getMicroSpecialtyId() { return microSpecialtyId; } public void setMicroSpecialtyId(Long v) { microSpecialtyId = v; }
    public Long getTeacherId() { return teacherId; } public void setTeacherId(Long v) { teacherId = v; }
    public String getTeacherName() { return teacherName; } public void setTeacherName(String v) { teacherName = v; }
    public String getTeacherAvatar() { return teacherAvatar; } public void setTeacherAvatar(String v) { teacherAvatar = v; }
    public String getRole() { return role; } public void setRole(String v) { role = v; }
    public Long getCourseId() { return courseId; } public void setCourseId(Long v) { courseId = v; }
    public String getCourseTitle() { return courseTitle; } public void setCourseTitle(String v) { courseTitle = v; }
    public String getResponsibility() { return responsibility; } public void setResponsibility(String v) { responsibility = v; }
    public String getInviteStatus() { return inviteStatus; } public void setInviteStatus(String v) { inviteStatus = v; }
    public LocalDateTime getInviteExpiresAt() { return inviteExpiresAt; } public void setInviteExpiresAt(LocalDateTime v) { inviteExpiresAt = v; }
}
