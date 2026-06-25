package com.microcourse.controller;

import com.microcourse.BaseIntegrationTest;

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
        // 清除登录失败计数，避免影响其他测试
        clearAdminToken();
        com.microcourse.util.RedisUtil ru = applicationContext.getBean(com.microcourse.util.RedisUtil.class);
        ru.delete("login:lock:admin");
    }

    @Test
    @DisplayName("不存在的用户登录返回401")
    void loginWithNonexistentUser_Returns401() throws Exception {
        // 清除该用户名的登录失败计数：Redis 为共享实例（localhost:6379），本测试每次运行都会
        // 对 login:lock:nobody_99999 +1 却从不清理，多次运行后累积 >=5 会被锁定返回 423。
        // 与 loginWithInvalidPassword_Returns401 / BaseIntegrationTest 的清理逻辑保持一致。
        applicationContext.getBean(com.microcourse.util.RedisUtil.class).delete("mc:login:lock:nobody_99999");
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
    @DisplayName("无Token访问返回401")
    void meWithoutToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
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
    @DisplayName("使用无效刷新Token返回401")
    void refreshToken_Invalid_Returns401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"dummy-invalid-token\"}"))
                .andExpect(status().isUnauthorized());
    }
}
