package com.microcourse.controller;

import com.jayway.jsonpath.JsonPath;
import com.microcourse.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 打卡 CheckInController 集成测试。
 *
 * <p>覆盖 3 个端点（打卡/历史/连续天数）+ 异常场景。</p>
 */
@DisplayName("CheckInController · 打卡集成测试")
@Sql(scripts = "/sql/p0-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class CheckInControllerTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM check_ins");
    }

    private String studentToken() throws Exception {
        return "Bearer " + loginAs("student", P0_PASSWORD);
    }

    @Test
    @DisplayName("POST /api/check-ins · 学生打卡成功")
    void checkIn_Student_ReturnsOk() throws Exception {
        mockMvc.perform(post("/api/check-ins")
                        .header("Authorization", studentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.checkinDate").exists())
                .andExpect(jsonPath("$.data.streakDays").isNumber())
                .andExpect(jsonPath("$.data.userId").isNumber());
    }

    @Test
    @DisplayName("POST /api/check-ins · 打卡后返回当天日期")
    void checkIn_ReturnsTodayDate() throws Exception {
        String today = LocalDate.now().toString();
        MvcResult result = mockMvc.perform(post("/api/check-ins")
                        .header("Authorization", studentToken()))
                .andExpect(status().isOk())
                .andReturn();

        // response may use "checkinDate" field
        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains(today) || body.contains(LocalDate.now().toString()),
                "打卡记录应包含当天日期, body=" + body);
    }

    @Test
    @DisplayName("POST /api/check-ins · 管理员（非 STUDENT）打卡 → 403")
    void checkIn_Admin_Returns403() throws Exception {
        mockMvc.perform(post("/api/check-ins")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/check-ins/my · 学生查询打卡历史（默认 30 天）")
    void getMyCheckIns_Student_ReturnsList() throws Exception {
        // First check in
        mockMvc.perform(post("/api/check-ins")
                        .header("Authorization", studentToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/check-ins/my")
                        .header("Authorization", studentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /api/check-ins/my?days=7 · 指定天数参数")
    void getMyCheckIns_WithDaysParam_ReturnsFilteredList() throws Exception {
        mockMvc.perform(post("/api/check-ins")
                        .header("Authorization", studentToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/check-ins/my")
                        .param("days", "7")
                        .header("Authorization", studentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /api/check-ins/streak · 学生查询连续天数")
    void getStreak_Student_ReturnsNumber() throws Exception {
        // First check in
        mockMvc.perform(post("/api/check-ins")
                        .header("Authorization", studentToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/check-ins/streak")
                        .header("Authorization", studentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isNumber());
    }

    @Test
    @DisplayName("GET /api/check-ins/streak · 未打卡时连续天数为 0")
    void getStreak_NoCheckIn_ReturnsZero() throws Exception {
        mockMvc.perform(get("/api/check-ins/streak")
                        .header("Authorization", studentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(0));
    }

    @Test
    @DisplayName("POST /api/check-ins · 重复打卡当天返回已有记录（幂等）")
    void checkIn_DuplicateSameDay_Idempotent() throws Exception {
        MvcResult first = mockMvc.perform(post("/api/check-ins")
                        .header("Authorization", studentToken()))
                .andExpect(status().isOk())
                .andReturn();
        Number firstId = JsonPath.read(first.getResponse().getContentAsString(), "$.data.id");

        MvcResult second = mockMvc.perform(post("/api/check-ins")
                        .header("Authorization", studentToken()))
                .andExpect(status().isOk())
                .andReturn();
        Number secondId = JsonPath.read(second.getResponse().getContentAsString(), "$.data.id");

        // Should return the same record (idempotent)
        assertTrue(firstId.longValue() == secondId.longValue() || firstId == secondId,
                "同一天重复打卡应返回相同或已存在的记录");
    }

    @Test
    @DisplayName("所有端点 · 未认证返回 401")
    void withoutAuth_Returns401() throws Exception {
        mockMvc.perform(post("/api/check-ins")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/check-ins/my")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/check-ins/streak")).andExpect(status().isUnauthorized());
    }
}
