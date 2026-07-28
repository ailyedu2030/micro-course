package com.microcourse.controller;

import com.microcourse.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * NotificationController 集成测试（通知管理）。
 *
 * <p>覆盖 5 个核心场景：通知列表、标记已读、全部已读、未读计数、发送通知。</p>
 */
@DisplayName("NotificationController · 通知集成测试")
@Sql(scripts = "/sql/p0-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class NotificationControllerTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM notifications WHERE id > 0");
        // 防御性:重置 p0_teacher 角色 + 清除 UserStatusCheckFilter Redis 缓存
        // (防止其他 test class 状态污染 + Redis 缓存 30s TTL)
        jdbc.update("UPDATE users SET role = 'TEACHER', status = 1 WHERE id = 6 AND username = 'p0_teacher'");
        try {
            com.microcourse.util.RedisUtil redisUtil = applicationContext.getBean(com.microcourse.util.RedisUtil.class);
            redisUtil.delete("mc:user:status:6");
        } catch (Exception ignored) {
            // 缓存清除失败不影响测试
        }
    }

    private String bearerTeacher() throws Exception {
        return "Bearer " + loginAs("p0_teacher", P0_PASSWORD);
    }

    private String bearerStudent() throws Exception {
        return "Bearer " + loginAs("student", P0_PASSWORD);
    }

    @Test
    @DisplayName("GET /api/notifications · 已登录用户可查看通知列表（空列表正常返回）")
    void getMyNotifications_Authenticated_ReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/notifications")
                        .param("page", "0")
                        .param("size", "20")
                        .header("Authorization", bearerStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.totalElements").isNumber());
    }

    @Test
    @DisplayName("GET /api/notifications · 未认证返回 401")
    void getMyNotifications_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/notifications/unread-count · 已登录用户可查看未读计数")
    void getUnreadCount_Authenticated_ReturnsCount() throws Exception {
        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", bearerStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isNumber());
    }

    @Test
    @DisplayName("PUT /api/notifications/read-all · 全部标记已读成功")
    void markAllAsRead_Authenticated_ReturnsOk() throws Exception {
        mockMvc.perform(put("/api/notifications/read-all")
                        .header("Authorization", bearerStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("POST /api/notifications · TEACHER 可发送通知")
    void send_Teacher_ReturnsNotification() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/notifications")
                        .header("Authorization", bearerTeacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":7,\"type\":\"COURSE_NOTICE\",\"title\":\"测试通知\",\"content\":\"这是一条测试通知内容\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("data"), "应返回通知数据，body=" + body);
    }
}
