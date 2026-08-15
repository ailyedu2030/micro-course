package com.microcourse.dto.microSpecialty;

import jakarta.validation.constraints.Size;

public class DropRequest {

    private boolean cascade;

    @Size(max = 500, message = "退出原因长度不能超过 500 字符")
    private String reason;

    public DropRequest() {}

    public boolean isCascade() { return cascade; }
    public void setCascade(boolean cascade) { this.cascade = cascade; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
