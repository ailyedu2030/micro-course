package com.microcourse.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 刷新 Token 请求
 */
public class RefreshRequest {

    @NotBlank
    private String refreshToken;

    public RefreshRequest() {
    }

    public RefreshRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}