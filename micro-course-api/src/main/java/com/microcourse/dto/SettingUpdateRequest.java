package com.microcourse.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 系统配置更新请求 DTO
 *
 * @author Phase9-Development-Team
 * @since 2026-06-13
 */
public class SettingUpdateRequest {

    @NotBlank(message = "配置键不能为空")
    private String key;

    @NotBlank(message = "配置值不能为空")
    private String value;

    public SettingUpdateRequest() {}

    public SettingUpdateRequest(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}