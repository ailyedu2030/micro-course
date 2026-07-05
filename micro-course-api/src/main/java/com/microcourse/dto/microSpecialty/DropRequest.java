package com.microcourse.dto.microSpecialty;

public class DropRequest {

    private boolean cascade;
    private String reason;

    public DropRequest() {}

    public boolean isCascade() { return cascade; }
    public void setCascade(boolean cascade) { this.cascade = cascade; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
