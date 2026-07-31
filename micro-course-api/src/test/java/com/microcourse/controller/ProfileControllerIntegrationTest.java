package com.microcourse.controller;

import com.microcourse.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ProfileController 集成测试 — 验证 /api/profile/* 别名路由可达。
 *
 * <p>背景：原 AuthController 类级 @RequestMapping("/api/auth") 下错误声明了
 * /api/profile/* 路径（实际产生 /api/auth/api/profile/... 伪路由）。
 * 本测试验证迁移至 ProfileController 后的正确路由映射。
 */
@DisplayName("ProfileController — /api/profile/* 别名路由")
public class ProfileControllerIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("GET /api/profile 使用有效 Token 返回用户信息（/api/auth/me 别名）")
    void getProfileWithValidTokenReturnsUserInfo() throws Exception {
        mockMvc.perform(get("/api/profile")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    @DisplayName("GET /api/profile 无 Token 返回 401")
    void getProfileWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /api/profile 使用有效 Token 更新个人信息")
    void updateProfileWithValidTokenSucceeds() throws Exception {
        mockMvc.perform(put("/api/profile")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"realName\":\"管理员2\",\"email\":\"admin2@test.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("PUT /api/profile 无有效 Token 返回 401")
    void updateProfileWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(put("/api/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"realName\":\"管理员2\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/profile/change-password 使用有效 Token 修改密码")
    void changePasswordWithValidTokenSucceeds() throws Exception {
        mockMvc.perform(post("/api/profile/change-password")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"admin123\",\"newPassword\":\"newAdmin1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("POST /api/profile/change-password 无 Token 返回 401")
    void changePasswordWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/profile/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"admin123\",\"newPassword\":\"newAdmin1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/profile 路由不等于 /api/auth/api/profile（验证无伪路由）")
    void profileRouteIsNotDoublePrefixed() throws Exception {
        // /api/auth/api/profile 应返回 404 而非 200
        mockMvc.perform(get("/api/auth/api/profile")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isNotFound());
    }
}
