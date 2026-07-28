package com.microcourse.controller;

import com.jayway.jsonpath.JsonPath;
import com.microcourse.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * GradeController 集成测试（评分管理）。
 *
 * <p>覆盖 6 个核心场景：学生我的成绩、教师分页查询、待批改列表、成绩创建、教师批改、手动评阅。</p>
 */
@DisplayName("GradeController · 评分管理集成测试")
@Sql(scripts = "/sql/p0-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class GradeControllerTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM exercise_records WHERE id > 0");
        jdbc.update("DELETE FROM grades WHERE id > 0");
    }

    private String bearerTeacher() throws Exception {
        return "Bearer " + loginAs("p0_teacher", P0_PASSWORD);
    }

    private String bearerStudent() throws Exception {
        return "Bearer " + loginAs("student", P0_PASSWORD);
    }

    @Test
    @DisplayName("GET /api/grades/my · 学生查看我的成绩，空列表正常返回")
    void getMyGrades_Student_ReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/grades/my")
                        .header("Authorization", bearerStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.totalElements").isNumber());
    }

    @Test
    @DisplayName("GET /api/grades/my?courseId=1 · 按课程筛选学生成绩")
    void getMyGrades_ByCourse_ReturnsFiltered() throws Exception {
        mockMvc.perform(get("/api/grades/my")
                        .param("courseId", "1")
                        .header("Authorization", bearerStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("GET /api/grades · 教师分页查询成绩（可指定课程筛选）")
    void page_Teacher_ReturnsPagedResult() throws Exception {
        mockMvc.perform(get("/api/grades")
                        .param("courseId", "1")
                        .param("page", "0")
                        .param("size", "10")
                        .header("Authorization", bearerTeacher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("GET /api/grades/my · TEACHER 角色无权限访问 → 403")
    void getMyGrades_Teacher_Returns403() throws Exception {
        mockMvc.perform(get("/api/grades/my")
                        .header("Authorization", bearerTeacher()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/grades/teacher-grade · 教师批改成绩（enrollmentId 不存在时返回 404）")
    void teacherGrade_NonExistingEnrollment_ReturnsBusinessError() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/grades/teacher-grade")
                        .header("Authorization", bearerTeacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enrollmentId\":999999,\"score\":85,\"comment\":\"批改评语\"}"))
                .andExpect(status().isNotFound())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("code") || body.contains("error") || body.contains("message"),
                "应返回业务错误，body=" + body);
    }

    @Test
    @DisplayName("POST /api/grades/{recordId}/manual-grade · 教师手动评阅（recordId 不存在返回业务错误）")
    void manualGrade_NonExistingRecord_ReturnsBusinessError() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/grades/999999/manual-grade")
                        .header("Authorization", bearerTeacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":1,\"score\":85,\"comment\":\"做得好\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(9005))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("code") || body.contains("message"),
                "应返回业务错误，body=" + body);
    }
}
