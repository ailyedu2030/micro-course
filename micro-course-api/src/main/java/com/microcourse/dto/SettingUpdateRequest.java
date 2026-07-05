package com.microcourse.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 系统配置更新请求 DTO
 * <p>
 * P0-1: 前端发 settingKey/settingValue，通过 @JsonProperty 映射到 key/value
 * P2: value 改为 @NotNull（允许空字符串，如清空 logoUrl）
 *
 * @author Phase9-Development-Team
 * @since 2026-06-13
 */
public class SettingUpdateRequest {

    @NotBlank(message = "配置键不能为空")
    @JsonProperty("settingKey")
    private String key;

    @NotNull(message = "配置值不能为 null")
    @JsonProperty("settingValue")
    private String value;

    @JsonProperty("valueType")
    private String valueType;

    public SettingUpdateRequest() {}

    public SettingUpdateRequest(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public String getValueType() { return valueType; }
    public void setValueType(String valueType) { this.valueType = valueType; }
}