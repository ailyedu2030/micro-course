package com.microcourse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("notification_preferences")
public class NotificationPreference {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("allow_site")
    private Boolean allowSite;

    @TableField("allow_email")
    private Boolean allowEmail;

    @TableField("allow_wechat")
    private Boolean allowWechat;

    @TableField("quiet_hours_start")
    private String quietHoursStart;

    @TableField("quiet_hours_end")
    private String quietHoursEnd;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public NotificationPreference() {}

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