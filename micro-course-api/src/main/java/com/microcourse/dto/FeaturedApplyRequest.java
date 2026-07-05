package com.microcourse.dto;

import jakarta.validation.constraints.NotBlank;

public class FeaturedApplyRequest {
    @NotBlank(message = "申请理由不能为空")
    private String reason;

    public FeaturedApplyRequest() {}
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
