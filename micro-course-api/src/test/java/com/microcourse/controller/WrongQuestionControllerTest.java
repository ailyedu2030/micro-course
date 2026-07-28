package com.microcourse.controller;

import com.microcourse.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WrongQuestionController 集成测试（错题集）。
 *
 * <p>覆盖 5 个核心场景：学生错题列表、按课程/章节筛选、权限校验（403/401）。</p>
 */
@DisplayName("WrongQuestionController · 错题集集成测试")
@Sql(scripts = "/sql/p0-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class WrongQuestionControllerTest extends BaseIntegrationTest {

    private String bearerStudent() throws Exception {
        return "Bearer " + loginAs("student", P0_PASSWORD);
    }

    private String bearerTeacher() throws Exception {
        return "Bearer " + loginAs("p0_teacher", P0_PASSWORD);
    }

    @Test
    @DisplayName("GET /api/wrong-questions/my · 学生可查看自己的错题集")
    void getMyWrongQuestions_Student_ReturnsList() throws Exception {
        mockMvc.perform(get("/api/wrong-questions/my")
                        .header("Authorization", bearerStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /api/wrong-questions/my?courseId=1 · 按课程筛选错题")
    void getMyWrongQuestions_ByCourse_ReturnsOk() throws Exception {
        mockMvc.perform(get("/api/wrong-questions/my")
                        .param("courseId", "1")
                        .header("Authorization", bearerStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /api/wrong-questions/my?chapterId=1 · 按章节筛选错题")
    void getMyWrongQuestions_ByChapter_ReturnsOk() throws Exception {
        mockMvc.perform(get("/api/wrong-questions/my")
                        .param("chapterId", "1")
                        .header("Authorization", bearerStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /api/wrong-questions/my · TEACHER 角色无权限查看学生错题 → 403")
    void getMyWrongQuestions_Teacher_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/wrong-questions/my")
                        .header("Authorization", bearerTeacher()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/wrong-questions/my · 未认证返回 401")
    void getMyWrongQuestions_Unauthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/wrong-questions/my"))
                .andExpect(status().isUnauthorized());
    }
}
