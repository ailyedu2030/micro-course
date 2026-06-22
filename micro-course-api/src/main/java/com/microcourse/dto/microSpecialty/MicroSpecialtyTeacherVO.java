package com.microcourse.dto.microSpecialty;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MicroSpecialtyTeacherVO {

    private Long id;
    private Long microSpecialtyId;
    private Long teacherId;
    private String teacherName;
    private String teacherAvatar;
    private String roleLabel;
    private Long courseId;
    private String courseTitle;
    private String responsibility;
    private String inviteStatus;
    private LocalDateTime inviteExpiresAt;

    public MicroSpecialtyTeacherVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getMicroSpecialtyId() { return microSpecialtyId; }
    public void setMicroSpecialtyId(Long microSpecialtyId) { this.microSpecialtyId = microSpecialtyId; }

    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }

    public String getTeacherName() { return teacherName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }

    public String getTeacherAvatar() { return teacherAvatar; }
    public void setTeacherAvatar(String teacherAvatar) { this.teacherAvatar = teacherAvatar; }

    public String getRoleLabel() { return roleLabel; }
    public void setRoleLabel(String roleLabel) { this.roleLabel = roleLabel; }

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }

    public String getCourseTitle() { return courseTitle; }
    public void setCourseTitle(String courseTitle) { this.courseTitle = courseTitle; }

    public String getResponsibility() { return responsibility; }
    public void setResponsibility(String responsibility) { this.responsibility = responsibility; }

    public String getInviteStatus() { return inviteStatus; }
    public void setInviteStatus(String inviteStatus) { this.inviteStatus = inviteStatus; }

    public LocalDateTime getInviteExpiresAt() { return inviteExpiresAt; }
    public void setInviteExpiresAt(LocalDateTime inviteExpiresAt) { this.inviteExpiresAt = inviteExpiresAt; }
}
