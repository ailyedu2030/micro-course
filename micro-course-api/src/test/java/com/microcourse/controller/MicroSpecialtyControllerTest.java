package com.microcourse.controller;

import com.microcourse.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MicroSpecialtyController 集成测试（微专业 广场/列表/详情/教师团队/角色/统计）。
 *
 * <p>覆盖 8 个核心场景：广场查询、分页列表（已认证/未认证/keyword 筛选）、详情（存在/不存在）、
 * 教师团队、角色查询、数据统计。</p>
 *
 * <p>账号约定（p0-seed.sql）：
 * <ul>
 *   <li>ADMIN: admin / admin123 (id=1)</li>
 *   <li>TEACHER: p0_teacher / student123 (id=6)</li>
 *   <li>STUDENT: student / student123 (id=7)</li>
 * </ul>
 */
@DisplayName("MicroSpecialtyController · 微专业集成测试")
@Sql(scripts = "/sql/p0-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class MicroSpecialtyControllerTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM micro_specialty_courses WHERE id > 1");
        jdbc.update("DELETE FROM micro_specialty_teachers WHERE id > 1");
        jdbc.update("DELETE FROM micro_specialties WHERE id > 1");
    }

    @Test
    @DisplayName("square · 广场数据 permitAll，无需认证")
    void square_PermitAll_ReturnsOk() throws Exception {
        mockMvc.perform(get("/api/micro-specialties/square"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isMap());
    }

    @Test
    @DisplayName("page · 已认证用户分页查询")
    void page_Authenticated_ReturnsPagedResult() throws Exception {
        mockMvc.perform(get("/api/micro-specialties")
                        .param("page", "0").param("size", "10")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("page · 未认证返回 401")
    void page_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/micro-specialties"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("page · 带 keyword 筛选返回结果")
    void page_WithKeyword_ReturnsOk() throws Exception {
        mockMvc.perform(get("/api/micro-specialties")
                        .param("keyword", "P0测试").param("page", "0").param("size", "10")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("detail · 已存在微专业返回详情")
    void getDetail_ExistingId_ReturnsDetail() throws Exception {
        mockMvc.perform(get("/api/micro-specialties/1")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("detail · 不存在的 ID 返回业务错误")
    void getDetail_NonExisting_ReturnsError() throws Exception {
        mockMvc.perform(get("/api/micro-specialties/99999"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("teachers · 教师团队 permitAll 返回列表")
    void listTeachers_PermitAll_ReturnsList() throws Exception {
        mockMvc.perform(get("/api/micro-specialties/1/teachers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("my-role · 已登录用户可查询自身角色")
    void myRole_Authenticated_ReturnsRole() throws Exception {
        mockMvc.perform(get("/api/micro-specialties/1/my-role")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
