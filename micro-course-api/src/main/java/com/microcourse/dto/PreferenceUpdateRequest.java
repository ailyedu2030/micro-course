package com.microcourse.dto;

public class PreferenceUpdateRequest {

    private Boolean allowSite;
    private Boolean allowEmail;
    private Boolean allowWechat;
    private String quietHoursStart;
    private String quietHoursEnd;
    private String extraPreferences;

    public PreferenceUpdateRequest() {}

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
    public String getExtraPreferences() { return extraPreferences; }
    public void setExtraPreferences(String extraPreferences) { this.extraPreferences = extraPreferences; }
}