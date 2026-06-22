package com.microcourse.dto.microSpecialty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class MicroSpecialtyFeaturedApplyRequest {

    @NotBlank(message = "申请理由不能为空")
    @Size(max = 500, message = "申请理由最多500个字符")
    private String reason;

    public MicroSpecialtyFeaturedApplyRequest() {}

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
