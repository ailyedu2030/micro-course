package com.microcourse.controller;

import com.microcourse.BaseIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import com.microcourse.enums.UserRole;
import com.microcourse.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R8 P0-2: 成绩批改流程集成测试（含 P0-5 通知验证）。
 *
 * 覆盖场景：
 * 1. 教师可对自己授课课程的学生成绩进行批改
 * 2. 批改后成绩可查询
 * 3. 教师不可批改其他教师课程的学生成绩（越权阻断）
 * 4. 成绩录入后触发通知（不抛异常即通过）
 */
@DisplayName("R8 成绩批改流程")
@Sql(scripts = "/sql/p0-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class GradeFlowIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JwtUtil jwtUtil;

    // seed 数据: p0_teacher (id=6), p0_student (id=7), courses teacher_id=6, enrollments exist
    private static final Long TEACHER_ID = 6L;
    private static final Long STUDENT_ID = 7L;

    private Long ensureEnrollment(String studentToken, Long courseId) throws Exception {
        // 学生报名课程（返回 enrollmentId）
        String body = "{\"courseId\":" + courseId + ",\"sourceChannel\":\"direct\"}";
        MvcResult result = mockMvc.perform(post("/api/enrollments")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andReturn();
        String response = result.getResponse().getContentAsString();
        Object id = com.jayway.jsonpath.JsonPath.read(response, "$.data.id");
        return ((Number) id).longValue();
    }

    @Test
    @DisplayName("教师批改自己课程学生的成绩 — 成功")
    void teacherGradeOwnStudent_success() throws Exception {
        String teacherToken = jwtUtil.generateToken(TEACHER_ID, "p0_teacher", UserRole.TEACHER, 1L);
        String studentToken = jwtUtil.generateToken(STUDENT_ID, "student", UserRole.STUDENT, 1L);

        Long courseId = jdbc.queryForObject(
                "SELECT id FROM courses WHERE teacher_id = ? AND deleted_at IS NULL ORDER BY id LIMIT 1",
                Long.class, TEACHER_ID);
        assertTrue(courseId > 0, "Seed must contain a course for teacher 3");

        // 学生报名后获取 enrollmentId
        Long enrollmentId = ensureEnrollment(studentToken, courseId);

        // 教师批改: POST /api/grades/teacher-grade
        String body = "{\"enrollmentId\":" + enrollmentId + ",\"score\":85,\"comment\":\"做得不错\"}";
        MvcResult result = mockMvc.perform(post("/api/grades/teacher-grade")
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        String response = result.getResponse().getContentAsString();
        Object score = JsonPath.read(response, "$.data.score");
        assertEquals(85, ((Number) score).intValue(), "Score should match input");

        // 验证成绩可查询: GET /api/grades/{id}
        Object gradeId = JsonPath.read(response, "$.data.id");
        mockMvc.perform(get("/api/grades/" + ((Number) gradeId).longValue())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(85));
    }

    @Test
    @DisplayName("教师不可批改不属于自己课程的学生的成绩 — 403 阻断")
    void teacherGradeOtherTeacherCourse_forbidden() throws Exception {
        // P1-C 修复: 创建用户99确保UserStatusCheckFilter通过(否则status=DELETED->401)
        jdbc.update("INSERT INTO users (id, username, password, real_name, role, status, cas_bound, created_at, updated_at) " +
                "VALUES (99, 'otherTeacher', '$2b$12$8INfOluI..wPsed6wvZSsOxfoH/dzsxaXvPR5ABQffWVKyjH7gcmK', " +
                "'其他教师', 'TEACHER', 1, false, now(), now()) ON CONFLICT (id) DO NOTHING");
        String studentToken = jwtUtil.generateToken(STUDENT_ID, "student", UserRole.STUDENT, 1L);
        String teacherBToken = jwtUtil.generateToken(99L, "otherTeacher", UserRole.TEACHER, 1L);

        // 老师 B 先用一样的课程，但因为是不同教师，越权校验在 service 层会阻断
        Long otherCourseId = jdbc.queryForObject(
                "SELECT id FROM courses WHERE teacher_id = ? AND deleted_at IS NULL ORDER BY id LIMIT 1",
                Long.class, TEACHER_ID);

        Long otherEnrollmentId = ensureEnrollment(studentToken, otherCourseId);

        String body = "{\"enrollmentId\":" + otherEnrollmentId + ",\"score\":90}";
        mockMvc.perform(post("/api/grades/teacher-grade")
                .header("Authorization", "Bearer " + teacherBToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("成绩批改后触发通知（不抛异常即通过）")
    void teacherGrade_triggersNotification_success() throws Exception {
        String teacherToken = jwtUtil.generateToken(TEACHER_ID, "p0_teacher", UserRole.TEACHER, 1L);
        String studentToken = jwtUtil.generateToken(STUDENT_ID, "student", UserRole.STUDENT, 1L);

        Long courseId = jdbc.queryForObject(
                "SELECT id FROM courses WHERE teacher_id = ? AND deleted_at IS NULL ORDER BY id LIMIT 1",
                Long.class, TEACHER_ID);
        Long enrollmentId = ensureEnrollment(studentToken, courseId);

        // 批改成绩 — 内部应触发 notificationService.notifyAsync（不抛异常即通过）
        String body = "{\"enrollmentId\":" + enrollmentId + ",\"score\":92,\"comment\":\"优秀\"}";
        mockMvc.perform(post("/api/grades/teacher-grade")
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("未登录用户不可提交成绩 — 401")
    void teacherGrade_unauthenticated_401() throws Exception {
        String body = "{\"enrollmentId\":1,\"score\":85}";
        mockMvc.perform(post("/api/grades/teacher-grade")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isUnauthorized());
    }
}
