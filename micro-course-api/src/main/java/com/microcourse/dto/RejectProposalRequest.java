package com.microcourse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RejectProposalRequest {

    @NotBlank(message = "拒绝原因不能为空")
    @Size(max = 500, message = "拒绝原因长度不能超过 500 字符")
    private String reason;

    public RejectProposalRequest() {}
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
