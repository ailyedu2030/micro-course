package com.microcourse.dto.microSpecialty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class MicroSpecialtyRejectRequest {

    @NotBlank(message = "拒绝原因不能为空")
    @Size(min = 10, max = 500, message = "拒绝原因至少10个字符，不能超过500字")
    private String reason;

    public MicroSpecialtyRejectRequest() {}

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
