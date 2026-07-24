package com.microcourse.controller;

import com.microcourse.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("教师课程封面集成测试")
@Sql(scripts = "/sql/p0-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class TeacherCourseCoverIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("GET /api/teachers/courses 保留站内课程封面地址")
    void getMyCoursesKeepsRelativeCoverUrl() throws Exception {
        jdbc.update("UPDATE courses SET cover_url = ? WHERE id = ?", "covers/p0-course-cover.jpg", 1L);
        String teacherToken = "Bearer " + loginAs("p0_teacher", "student123");

        mockMvc.perform(get("/api/teachers/courses")
                        .header("Authorization", teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items[0].id").value(1))
                .andExpect(jsonPath("$.data.items[0].cover").value("/api/files/covers/p0-course-cover.jpg"));
    }
}
