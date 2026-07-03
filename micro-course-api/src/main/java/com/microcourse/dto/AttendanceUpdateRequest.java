package com.microcourse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class AttendanceUpdateRequest {

    @NotBlank(message = "签到状态不能为空")
    @Pattern(regexp = "PRESENT|LATE|ABSENT|EXCUSED", message = "无效的签到状态")
    private String status;

    public AttendanceUpdateRequest() {}

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
