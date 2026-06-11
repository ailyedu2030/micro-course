package com.microcourse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.MediaType;

public class AuthIntegrationTest extends BaseIntegrationTest {

    private String loginJson(String u, String p) {
        return "{\"username\":\"" + u + "\",\"password\":\"" + p + "\"}";
    }

    @Test
    @DisplayName("有效凭据登录成功返回Token")
    void loginWithValidCredentials_ReturnsToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists());
    }

    @Test
    @DisplayName("无效密码登录返回401")
    void loginWithInvalidPassword_Returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("admin", "wrongpassword")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("不存在的用户登录返回401")
    void loginWithNonexistentUser_Returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("nobody_99999", "x")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("使用有效Token获取当前用户信息成功")
    void meWithValidToken_ReturnsUserInfo() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    @DisplayName("无Token访问返回403")
    void meWithoutToken_Returns403() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("无效Token访问返回4xx")
    void meWithInvalidToken_ReturnsError() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("登出使Token失效后无法继续使用")
    void logoutInvalidatesToken() throws Exception {
        String token = loginAs("admin", "admin123");
        assertNotNull(token);

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("刷新Token获取新AccessToken成功")
    void refreshToken_Success() throws Exception {
        String token = loginAs("admin", "admin123");
        mockMvc.perform(post("/api/auth/refresh").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
