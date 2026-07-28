package com.microcourse.controller;

import com.microcourse.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * OperationLogController 集成测试（操作日志）。
 *
 * <p>覆盖 5 个核心场景：ADMIN 查询日志列表、按 action/module/日期筛选、TEACHER 权限拒绝。</p>
 */
@DisplayName("OperationLogController · 操作日志集成测试")
@Sql(scripts = "/sql/p0-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class OperationLogControllerTest extends BaseIntegrationTest {

    private String bearerTeacher() throws Exception {
        return "Bearer " + loginAs("p0_teacher", P0_PASSWORD);
    }

    private String bearerStudent() throws Exception {
        return "Bearer " + loginAs("student", P0_PASSWORD);
    }

    @Test
    @DisplayName("GET /api/operation-logs · ADMIN 可查询操作日志列表（分页正常返回）")
    void page_Admin_ReturnsPagedResult() throws Exception {
        mockMvc.perform(get("/api/operation-logs")
                        .param("page", "0")
                        .param("size", "10")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.totalElements").isNumber());
    }

    @Test
    @DisplayName("GET /api/operation-logs?action= · 按操作类型筛选")
    void page_FilterByAction_ReturnsOk() throws Exception {
        mockMvc.perform(get("/api/operation-logs")
                        .param("action", "用户登录")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("GET /api/operation-logs?module= · 按模块名称筛选")
    void page_FilterByModule_ReturnsOk() throws Exception {
        mockMvc.perform(get("/api/operation-logs")
                        .param("module", "Auth")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("GET /api/operation-logs?startTime=&endTime= · 按日期范围筛选")
    void page_FilterByDateRange_ReturnsOk() throws Exception {
        mockMvc.perform(get("/api/operation-logs")
                        .param("startTime", "2026-01-01")
                        .param("endTime", "2026-12-31")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("GET /api/operation-logs · TEACHER 无权限访问 → 403")
    void page_Teacher_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/operation-logs")
                        .header("Authorization", bearerTeacher()))
                .andExpect(status().isForbidden());
    }
}
