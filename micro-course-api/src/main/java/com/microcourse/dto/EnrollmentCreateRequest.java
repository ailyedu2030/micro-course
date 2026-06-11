package com.microcourse.dto;

import jakarta.validation.constraints.NotNull;

public class EnrollmentCreateRequest {

    @NotNull(message = "课程ID不能为空")
    private Long courseId;

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    private String sourceChannel;

    public EnrollmentCreateRequest() {}

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getSourceChannel() { return sourceChannel; }
    public void setSourceChannel(String sourceChannel) { this.sourceChannel = sourceChannel; }
}
