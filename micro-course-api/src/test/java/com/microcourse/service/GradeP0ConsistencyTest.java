package com.microcourse.service;

import com.microcourse.BaseIntegrationTest;

import com.jayway.jsonpath.JsonPath;
import com.microcourse.enums.UserRole;
import com.microcourse.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0 成绩一致性回归测试（集成式）。
 *
 * <p>覆盖场景：
 * <ol>
 *   <li>{@code teacherGrade} 已 COMPLETED 选课不可修改</li>
 *   <li>{@code manualGrade} 非课程所属教师的批改被 NO_PERMISSION 阻断</li>
 *   <li>正常教师批改路径不回归（正向验证）</li>
 *   <li>ADMIN 可批改任意课程成绩</li>
 * </ol>
 *
 * <p>乐观锁 {@code @Version} 冲突单元测试请见 {@code GradeServiceImplVersionConflictTest}。
 * 种子数据：p0-seed.sql（teacher=6 / student=7 / 课程 1..4 已发布免费）。
 * 每测试使用独立的 courseId 避免 enrollment 唯一约束冲突。
 */
@DisplayName("P0 成绩一致性集成测试 — COMPLETED/权限/回归")
@Sql(scripts = "/sql/p0-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@TestMethodOrder(MethodOrderer.MethodName.class)
class GradeP0ConsistencyTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JwtUtil jwtUtil;

    private static final Long TEACHER_ID = 6L;
    private static final Long STUDENT_ID = 7L;

    // ========================================================================
    // 1. teacherGrade 已 COMPLETED 选课不可修改
    // ========================================================================

    @Test
    @DisplayName("[P0-1] teacherGrade 在 COMPLETED 选课上抛 BAD_REQUEST_PARAM 阻断")
    void teacherGrade_completedEnrollment_throwsError() throws Exception {
        String teacherToken = jwtUtil.generateToken(TEACHER_ID, "p0_teacher", UserRole.TEACHER, 1L);
        String studentToken = jwtUtil.generateToken(STUDENT_ID, "student", UserRole.STUDENT, 1L);

        // 使用课程 2（不与其他测试共享）
        Long courseId = 2L;
        Long enrollmentId = ensureEnrollment(studentToken, courseId);
        // 把 enrollment 标记为 COMPLETED（终态）
        jdbc.update("UPDATE enrollments SET enrollment_status = 'COMPLETED' WHERE id = ?", enrollmentId);

        // 尝试批改 → 应被 COMPLETED 阻断
        String body = "{\"enrollmentId\":" + enrollmentId + ",\"score\":85}";
        mockMvc.perform(post("/api/grades/teacher-grade")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(9005));
    }

    // ========================================================================
    // 2. manualGrade 非课程教师的批改被 NO_PERMISSION 阻断
    // ========================================================================

    @Test
    @DisplayName("[P0-2] manualGrade 非课程教师越权批改被 403 阻断")
    void manualGrade_otherTeacherCourse_forbidden() throws Exception {
        jdbc.update("INSERT INTO users (id, username, password, real_name, role, status, cas_bound, created_at, updated_at) "
                + "VALUES (99, 'otherTeacher', "
                + "'$2b$12$8INfOluI..wPsed6wvZSsOxfoH/dzsxaXvPR5ABQffWVKyjH7gcmK', "
                + "'其他教师', 'TEACHER', 1, false, now(), now()) ON CONFLICT (id) DO NOTHING");
        String otherTeacherToken = jwtUtil.generateToken(99L, "otherTeacher", UserRole.TEACHER, 1L);

        // 创建一条属于 teacher=6 课程的 exercise + record（使用课程 3）
        Long courseId = 3L;
        jdbc.update("INSERT INTO course_chapters (id, course_id, title, sort_order, version, created_at, updated_at) "
                + "VALUES (91001, ?, 'P0测试章节', 1, 0, now(), now()) ON CONFLICT (id) DO NOTHING", courseId);
        jdbc.update("INSERT INTO questions (id, course_id, teacher_id, question_type, content, answer, "
                + "version, status, created_at, updated_at) "
                + "VALUES (91001, ?, ?, 'SINGLE_CHOICE', '测试题:选A', 'A', 0, 1, now(), now()) "
                + "ON CONFLICT (id) DO NOTHING", courseId, TEACHER_ID);
        jdbc.update("INSERT INTO exercises (id, chapter_id, course_id, title, pass_score, "
                + "max_attempts, total_score, question_count, version, created_at, updated_at) "
                + "VALUES (91001, 91001, ?, 'P0-2测试练习', 0, 0, 100, 1, 0, now(), now()) "
                + "ON CONFLICT (id) DO NOTHING", courseId);
        jdbc.update("INSERT INTO exercise_questions (id, exercise_id, question_id, score, sort_order) "
                + "VALUES (91001, 91001, 91001, 100, 1) ON CONFLICT (id) DO NOTHING");
        String answers = "[{\"questionId\":91001,\"answer\":\"A\",\"fullScore\":100,\"needsManualGrading\":true}]";
        jdbc.update("INSERT INTO exercise_records (id, exercise_id, user_id, attempt_no, answers, "
                + "score, total_score, needs_manual_grading, submitted_at, version) "
                + "VALUES (91001, 91001, ?, 1, ?, 0, 100, true, now(), 0) "
                + "ON CONFLICT (id) DO UPDATE SET answers = ?",
                STUDENT_ID, answers, answers);

        // 其他教师(99)尝试批改 → exercise 的 course 属于 teacher=6 → NO_PERMISSION
        String body = "{\"questionId\":91001,\"score\":5,\"comment\":\"部分正确\"}";
        mockMvc.perform(post("/api/grades/91001/manual-grade")
                        .header("Authorization", "Bearer " + otherTeacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // ========================================================================
    // 3. 正常 teacherGrade 正向回归
    // ========================================================================

    @Test
    @DisplayName("[P0-3] 正常 teacherGrade 正向流程成功")
    void teacherGrade_normalPath_success() throws Exception {
        String teacherToken = jwtUtil.generateToken(TEACHER_ID, "p0_teacher", UserRole.TEACHER, 1L);
        String studentToken = jwtUtil.generateToken(STUDENT_ID, "student", UserRole.STUDENT, 1L);

        Long courseId = 1L;
        Long enrollmentId = ensureEnrollment(studentToken, courseId);

        // 教师批改
        String body = "{\"enrollmentId\":" + enrollmentId + ",\"score\":85,\"comment\":\"做得不错\"}";
        MvcResult result = mockMvc.perform(post("/api/grades/teacher-grade")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        // 验证成绩可查询
        Object gradeId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        assertNotNull(gradeId);

        mockMvc.perform(get("/api/grades/" + ((Number) gradeId).longValue())
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(85));
    }

    // ========================================================================
    // 4. ADMIN 可以批改任意课程成绩
    // ========================================================================

    @Test
    @DisplayName("[P0-4] ADMIN 可以批改任意课程成绩（正向不回归）")
    void teacherGrade_adminCanGradeAnyCourse() throws Exception {
        String studentToken = jwtUtil.generateToken(STUDENT_ID, "student", UserRole.STUDENT, 1L);
        // 使用课程 4（不与其他测试共享）
        Long courseId = 4L;
        Long enrollmentId = ensureEnrollment(studentToken, courseId);

        // ADMIN 批改
        String body = "{\"enrollmentId\":" + enrollmentId + ",\"score\":95}";
        mockMvc.perform(post("/api/grades/teacher-grade")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.score").value(95));
    }

    // ========================================================================
    // Cleanup
    // ========================================================================

    @BeforeEach
    void cleanEnrollments() {
        // 清理可能残留的 enrollment，确保每个测试独立
        jdbc.update("DELETE FROM enrollments WHERE course_id IN (1,2,3,4) AND user_id = ?", STUDENT_ID);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM grades WHERE id >= 91000");
        jdbc.update("DELETE FROM exercise_records WHERE id >= 91000");
        jdbc.update("DELETE FROM exercise_questions WHERE exercise_id >= 91000");
        jdbc.update("DELETE FROM exercises WHERE id >= 91000");
        jdbc.update("DELETE FROM questions WHERE id >= 91000");
        jdbc.update("DELETE FROM course_chapters WHERE id >= 91000");
        // enrollments: 只清理本测试创建的
        jdbc.update("DELETE FROM enrollments WHERE course_id IN (1,2,3,4) AND user_id = ?", STUDENT_ID);
        // 防止 FK 约束: courses_teacher_id_fkey (用户 99 不能有课程引用)
        jdbc.update("UPDATE courses SET teacher_id = 6 WHERE teacher_id = 99");
        jdbc.update("DELETE FROM users WHERE id = 99");
    }

    private Long ensureEnrollment(String studentToken, Long courseId) throws Exception {
        String body = "{\"courseId\":" + courseId + ",\"sourceChannel\":\"direct\"}";
        MvcResult result = mockMvc.perform(post("/api/enrollments")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        Object id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return ((Number) id).longValue();
    }
}
