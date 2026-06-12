package com.microcourse.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

public class TeacherStatusRequest {

    @NotNull(message = "审核状态不能为空")
    @Min(value = 0, message = "状态值无效")
    @Max(value = 2, message = "状态值无效")
    private Integer teacherStatus;

    private String reason;

    public TeacherStatusRequest() {}

    public Integer getTeacherStatus() { return teacherStatus; }
    public void setTeacherStatus(Integer teacherStatus) { this.teacherStatus = teacherStatus; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}