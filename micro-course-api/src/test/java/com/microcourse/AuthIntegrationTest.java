package com.microcourse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcourse.dto.LoginRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AuthIntegrationTest extends BaseIntegrationTest {

    private String loginJson(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
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
        String token = loginAs("admin", "admin123");
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    @DisplayName("无Token访问/api/auth/me返回403")
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
        assertNotNull(token, "登录应返回有效token");

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
