package com.microcourse.dto;

import jakarta.validation.constraints.NotBlank;

public class AttendanceUpdateRequest {

    @NotBlank(message = "签到状态不能为空")
    private String status;

    public AttendanceUpdateRequest() {}

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
