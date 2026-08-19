package com.microcourse.controller;

import com.microcourse.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1-C · OfflineSession 端点 ACADEMIC 角色权限回归测试（2026-08-12 修复）
 *
 * <p>背景：F-2026-08-10-05 (#220) 给前端 ACADEMIC 角色新增"线下课程"菜单入口，
 * 但后端 OfflineSessionController 全部端点 @PreAuthorize 仅开放 TEACHER/ADMIN/STUDENT，
 * ACADEMIC 点击入口首屏调用 GET /api/offline-sessions/{chapterId}/chapters 即 403，
 * 属客户可感知回归。权限矩阵 §1.27：线下课写操作=教师/管理员。</p>
 *
 * <p>修复：#220 前端移除 ACADEMIC 入口 + 路由 roles 收窄为 ADMIN。
 * 本测试锁死后端行为：ACADEMIC 访问 offline-session 端点必须 403（防未来后端
 * 误开放而未同步前端只读适配）。</p>
 *
 * <p>种子依赖 p0-seed.sql：course 1 / chapter 1 / section 1；ACADEMIC 用户来自
 * p1-academic-role-seed.sql（id=100, academic_user/student123）。</p>
 */
@DisplayName("P1-C OfflineSession ACADEMIC 权限回归")
@Sql(scripts = {"/sql/p0-seed.sql", "/sql/p1-academic-role-seed.sql"},
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class OfflineSessionAcademicPermissionTest extends BaseIntegrationTest {

    private String academicToken;
    private String teacherToken;
    private String adminToken;

    @BeforeEach
    void setupTokens() throws Exception {
        academicToken = "Bearer " + loginAs("academic_user", "student123");
        teacherToken = "Bearer " + loginAs("p0_teacher", "student123");
        adminToken = bearerAdmin();
    }

    // ────────────────────────────────────────────────────────────────
    // 1. ACADEMIC 访问 pageByChapter 列表 → 403（核心回归点）
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ACADEMIC → GET /api/offline-sessions/{chapterId}/chapters → 403")
    void academicCannotPageByChapter() throws Exception {
        mockMvc.perform(get("/api/offline-sessions/{chapterId}/chapters", 1L)
                        .param("page", "0").param("size", "20")
                        .header("Authorization", academicToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    @DisplayName("TEACHER → GET 同端点 → 200（基线参照）")
    void teacherCanPageByChapter() throws Exception {
        mockMvc.perform(get("/api/offline-sessions/{chapterId}/chapters", 1L)
                        .param("page", "0").param("size", "20")
                        .header("Authorization", teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("ADMIN → GET 同端点 → 200（基线参照）")
    void adminCanPageByChapter() throws Exception {
        mockMvc.perform(get("/api/offline-sessions/{chapterId}/chapters", 1L)
                        .param("page", "0").param("size", "20")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ────────────────────────────────────────────────────────────────
    // 2. ACADEMIC 写操作 → 403（防越权）
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ACADEMIC → POST 创建线下课 → 403")
    void academicCannotCreateOfflineSession() throws Exception {
        mockMvc.perform(post("/api/offline-sessions/{chapterId}/chapters", 1L)
                        .header("Authorization", academicToken)
                        .contentType("application/json")
                        .content("{\"title\":\"ACADEMIC 越权创建\",\"sessionDate\":\"2026-08-20\",\"startTime\":\"09:00:00\",\"endTime\":\"10:00:00\",\"location\":\"A101\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10003));
    }
}
