package com.microcourse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("micro_specialty_teachers")
public class MicroSpecialtyTeacher {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long microSpecialtyId;
    private Long teacherId;
    private String role;
    private Long courseId;
    private String responsibility;
    private String inviteStatus;
    private Long invitedBy;
    private LocalDateTime invitedAt;
    private LocalDateTime respondedAt;
    private LocalDateTime inviteExpiresAt;
    private LocalDateTime joinedAt;
    private LocalDateTime leftAt;
    private LocalDateTime createdAt;
    private Integer version;

    public MicroSpecialtyTeacher() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMicroSpecialtyId() { return microSpecialtyId; }
    public void setMicroSpecialtyId(Long microSpecialtyId) { this.microSpecialtyId = microSpecialtyId; }
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getResponsibility() { return responsibility; }
    public void setResponsibility(String responsibility) { this.responsibility = responsibility; }
    public String getInviteStatus() { return inviteStatus; }
    public void setInviteStatus(String inviteStatus) { this.inviteStatus = inviteStatus; }
    public Long getInvitedBy() { return invitedBy; }
    public void setInvitedBy(Long invitedBy) { this.invitedBy = invitedBy; }
    public LocalDateTime getInvitedAt() { return invitedAt; }
    public void setInvitedAt(LocalDateTime invitedAt) { this.invitedAt = invitedAt; }
    public LocalDateTime getRespondedAt() { return respondedAt; }
    public void setRespondedAt(LocalDateTime respondedAt) { this.respondedAt = respondedAt; }
    public LocalDateTime getInviteExpiresAt() { return inviteExpiresAt; }
    public void setInviteExpiresAt(LocalDateTime inviteExpiresAt) { this.inviteExpiresAt = inviteExpiresAt; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }
    public LocalDateTime getLeftAt() { return leftAt; }
    public void setLeftAt(LocalDateTime leftAt) { this.leftAt = leftAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
