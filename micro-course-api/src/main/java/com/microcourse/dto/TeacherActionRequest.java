package com.microcourse.dto;

public class TeacherActionRequest {
    private Long teacherId;
    private String action;
    private String reason;

    public TeacherActionRequest() {}
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
