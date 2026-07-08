package com.microcourse.dto;

/**
 * 用户 API Key 响应 DTO。
 *
 * <p>为安全起见，仅在创建/重新生成时返回明文 key。
 * 后续查询仅返回 {@code maskedKey}（首尾 4 位脱敏）。
 */
public class UserApiKeyResponse {

    /** 明文 API Key（仅生成时返回一次） */
    private String apiKey;

    /** 脱敏后的 API Key（例：ab12****wxyz） */
    private String maskedKey;

    /** 生成时间 */
    private String createdAt;

    public UserApiKeyResponse() {}

    public UserApiKeyResponse(String apiKey, String maskedKey, String createdAt) {
        this.apiKey = apiKey;
        this.maskedKey = maskedKey;
        this.createdAt = createdAt;
    }

    /** 仅脱敏（查看场景） */
    public static UserApiKeyResponse maskedOnly(String maskedKey, String createdAt) {
        return new UserApiKeyResponse(null, maskedKey, createdAt);
    }

    /** 全量返回（生成场景） */
    public static UserApiKeyResponse full(String apiKey, String maskedKey, String createdAt) {
        return new UserApiKeyResponse(apiKey, maskedKey, createdAt);
    }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getMaskedKey() { return maskedKey; }
    public void setMaskedKey(String maskedKey) { this.maskedKey = maskedKey; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    /** API key 脱敏：保留前 4 + 后 4，中间 **** */
    public static String mask(String key) {
        if (key == null || key.length() < 8) return "****";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}