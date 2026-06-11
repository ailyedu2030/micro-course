package com.microcourse.dto;

import java.time.LocalDateTime;

public class PreferenceVO {

    private Long id;
    private Long userId;
    private Boolean allowSite;
    private Boolean allowEmail;
    private Boolean allowWechat;
    private String quietHoursStart;
    private String quietHoursEnd;
    private LocalDateTime updatedAt;

    public PreferenceVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Boolean getAllowSite() { return allowSite; }
    public void setAllowSite(Boolean allowSite) { this.allowSite = allowSite; }
    public Boolean getAllowEmail() { return allowEmail; }
    public void setAllowEmail(Boolean allowEmail) { this.allowEmail = allowEmail; }
    public Boolean getAllowWechat() { return allowWechat; }
    public void setAllowWechat(Boolean allowWechat) { this.allowWechat = allowWechat; }
    public String getQuietHoursStart() { return quietHoursStart; }
    public void setQuietHoursStart(String quietHoursStart) { this.quietHoursStart = quietHoursStart; }
    public String getQuietHoursEnd() { return quietHoursEnd; }
    public void setQuietHoursEnd(String quietHoursEnd) { this.quietHoursEnd = quietHoursEnd; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}