package com.microcourse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 驳回请求 DTO。
 * 适用于课程审核驳回、微专业审核驳回等场景，携带必填驳回原因。
 */
public class RejectRequest {

    @NotBlank(message = "驳回原因不能为空")
    @Size(min = 10, max = 500, message = "驳回原因至少10个字符，不能超过500字")
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
