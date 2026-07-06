package com.microcourse.dto;

import jakarta.validation.constraints.Pattern;

public class PreferenceUpdateRequest {

    private Boolean allowSite;
    private Boolean allowEmail;
    private Boolean allowWechat;

    /** 静默时段开始时间 (HH:mm 格式，例如 22:00)，为 null 表示不启用静默时段 */
    @Pattern(regexp = "^([01]?\\d|2[0-3]):[0-5]\\d$|^$|^null$",
             message = "quietHoursStart 必须为 HH:mm 格式 (例如 22:00)")
    private String quietHoursStart;

    /** 静默时段结束时间 (HH:mm 格式，例如 08:00)，为 null 表示不启用静默时段 */
    @Pattern(regexp = "^([01]?\\d|2[0-3]):[0-5]\\d$|^$|^null$",
             message = "quietHoursEnd 必须为 HH:mm 格式 (例如 08:00)")
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