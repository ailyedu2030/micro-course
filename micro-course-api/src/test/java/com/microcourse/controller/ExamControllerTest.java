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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 考试/练习 ExamController 集成测试。
 *
 * <p>覆盖 2 个端点（我的考试列表/智能组卷）+ 权限场景。</p>
 */
@DisplayName("ExamController · 考试集成测试")
@Sql(scripts = "/sql/p0-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ExamControllerTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM exercise_records");
        jdbc.update("DELETE FROM exercise_questions");
        jdbc.update("DELETE FROM exercise_chapters");
        jdbc.update("DELETE FROM exercises");
    }

    private String studentToken() throws Exception {
        return "Bearer " + loginAs("student", P0_PASSWORD);
    }

    private String teacherToken() throws Exception {
        return "Bearer " + loginAs("p0_teacher", P0_PASSWORD);
    }

    @Test
    @DisplayName("GET /api/exams/my · 学生查看考试列表（空列表）")
    void myExams_StudentNoExams_ReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/exams/my")
                        .header("Authorization", studentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /api/exams/my · 非 STUDENT 角色 → 403")
    void myExams_Admin_Returns403() throws Exception {
        mockMvc.perform(get("/api/exams/my")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/exams/generate · 教师端智能组卷可选通")
    void generate_Teacher_No5xx() throws Exception {
        String body = "{"
                + "\"title\":\"集成测试考试\","
                + "\"courseId\":1,"
                + "\"chapterIds\":[1],"
                + "\"questionCounts\":{\"SINGLE\":1},"
                + "\"totalScore\":10,"
                + "\"timeLimit\":30"
                + "}";
        int code = mockMvc.perform(post("/api/exams/generate")
                        .header("Authorization", teacherToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getStatus();
        // 题库无题目时可能返回 4xx，但不允许 5xx
        assert code < 500 : "智能组卷不允许 5xx，实际=" + code;
    }

    @Test
    @DisplayName("POST /api/exams/generate · 学生越权 → 403")
    void generate_Student_Returns403() throws Exception {
        String body = "{"
                + "\"title\":\"考试\","
                + "\"courseId\":1,"
                + "\"questionCounts\":{\"SINGLE\":1},"
                + "\"totalScore\":10"
                + "}";
        mockMvc.perform(post("/api/exams/generate")
                        .header("Authorization", studentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/exams/generate · 参数校验失败 → 400")
    void generate_InvalidRequest_Returns400() throws Exception {
        // Missing title
        mockMvc.perform(post("/api/exams/generate")
                        .header("Authorization", teacherToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/exams/my · 未认证 → 401")
    void myExams_WithoutAuth_Returns401() throws Exception {
        mockMvc.perform(get("/api/exams/my")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/exams/generate · 未认证 → 401")
    void generate_WithoutAuth_Returns401() throws Exception {
        mockMvc.perform(post("/api/exams/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\"}"))
                .andExpect(status().isUnauthorized());
    }
}
