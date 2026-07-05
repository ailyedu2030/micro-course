package com.microcourse.dto;

import java.time.LocalDateTime;

/**
 * 系统配置视图对象
 *
 * @author Phase9-Development-Team
 * @since 2026-06-12
 */
public class AdminSettingVO {

    private Long id;
    private String settingKey;
    private String settingValue;
    private String valueType;
    private String description;
    private LocalDateTime updatedAt;

    public AdminSettingVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSettingKey() { return settingKey; }
    public void setSettingKey(String settingKey) { this.settingKey = settingKey; }
    public String getSettingValue() { return settingValue; }
    public void setSettingValue(String settingValue) { this.settingValue = settingValue; }
    public String getValueType() { return valueType; }
    public void setValueType(String valueType) { this.valueType = valueType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}