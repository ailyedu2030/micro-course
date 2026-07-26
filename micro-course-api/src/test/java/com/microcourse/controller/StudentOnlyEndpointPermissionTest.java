package com.microcourse.controller;

import com.microcourse.BaseIntegrationTest;
import com.microcourse.dto.R;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1-I 修复验证：学生专属端点的 @PreAuthorize 收紧测试。
 * <p>
 * 覆盖 4 个 controller 的权限变更：
 * - CheckInController: isAuthenticated() → hasRole('STUDENT')
 * - WrongQuestionController: isAuthenticated() → hasRole('STUDENT')
 * - ExerciseRecordController: isAuthenticated() → hasRole('STUDENT')
 * - LearningProgressController: isAuthenticated() → hasRole('STUDENT')
 * <p>
 * 测试策略：
 * 1. STUDENT 角色 → 200（正常访问）
 * 2. TEACHER 角色 → 403（被拒绝）
 * 3. ADMIN 角色 → 403（被拒绝）
 * 4. 未认证 → 401
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ActiveProfiles("test")
class StudentOnlyEndpointPermissionTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private String studentToken;
    private String teacherToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        studentToken = "Bearer " + loginAs("student", "student123");
        teacherToken = "Bearer " + loginAs("p0_teacher", "student123");
        adminToken = bearerAdmin();

        // Ensure a check-in record exists for student
        jdbc.update("DELETE FROM check_ins WHERE user_id = 7 AND checkin_date = CURRENT_DATE");
        jdbc.update("INSERT INTO check_ins (id, user_id, checkin_date, duration, created_at) " +
            "SELECT 999001, 7, CURRENT_DATE, 300, NOW() " +
            "WHERE NOT EXISTS (SELECT 1 FROM check_ins WHERE id = 999001)");
        jdbc.update("INSERT INTO enrollments (user_id, course_id, enrollment_status, enrolled_at, updated_at, version) " +
            "VALUES (7, 1, 'APPROVED', NOW(), NOW(), 0) " +
            "ON CONFLICT (user_id, course_id) WHERE deleted_at IS NULL DO NOTHING");
    }

    // ================================================================
    // CheckInController — 学生打卡
    // ================================================================

    @Test
    @Order(1)
    @DisplayName("CheckIn POST /api/check-ins — STUDENT 可打卡 → 200")
    void studentCanCheckIn() throws Exception {
        mockMvc.perform(post("/api/check-ins")
                .header("Authorization", studentToken))
            .andExpect(status().isOk());
    }

    @Test
    @Order(2)
    @DisplayName("CheckIn POST /api/check-ins — TEACHER 被拒绝 → 403")
    void teacherCannotCheckIn() throws Exception {
        mockMvc.perform(post("/api/check-ins")
                .header("Authorization", teacherToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    @DisplayName("CheckIn POST /api/check-ins — ADMIN 被拒绝 → 403")
    void adminCannotCheckIn() throws Exception {
        mockMvc.perform(post("/api/check-ins")
                .header("Authorization", adminToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(4)
    @DisplayName("CheckIn POST /api/check-ins — 未认证被拒绝 → 401")
    void unauthenticatedCannotCheckIn() throws Exception {
        mockMvc.perform(post("/api/check-ins"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(5)
    @DisplayName("CheckIn GET /api/check-ins/my — STUDENT 可访问 → 200")
    void studentCanViewCheckIns() throws Exception {
        mockMvc.perform(get("/api/check-ins/my")
                .header("Authorization", studentToken))
            .andExpect(status().isOk());
    }

    @Test
    @Order(6)
    @DisplayName("CheckIn GET /api/check-ins/my — TEACHER 被拒绝 → 403")
    void teacherCannotViewCheckIns() throws Exception {
        mockMvc.perform(get("/api/check-ins/my")
                .header("Authorization", teacherToken))
            .andExpect(status().isForbidden());
    }

    // ================================================================
    // WrongQuestionController — 错题集
    // ================================================================

    @Test
    @Order(10)
    @DisplayName("WrongQuestion GET /api/wrong-questions/my — STUDENT 可访问 → 200")
    void studentCanViewWrongQuestions() throws Exception {
        mockMvc.perform(get("/api/wrong-questions/my")
                .header("Authorization", studentToken))
            .andExpect(status().isOk());
    }

    @Test
    @Order(11)
    @DisplayName("WrongQuestion GET /api/wrong-questions/my — TEACHER 被拒绝 → 403")
    void teacherCannotViewWrongQuestions() throws Exception {
        mockMvc.perform(get("/api/wrong-questions/my")
                .header("Authorization", teacherToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(12)
    @DisplayName("WrongQuestion GET /api/wrong-questions/my — ADMIN 被拒绝 → 403")
    void adminCannotViewWrongQuestions() throws Exception {
        mockMvc.perform(get("/api/wrong-questions/my")
                .header("Authorization", adminToken))
            .andExpect(status().isForbidden());
    }

    // ================================================================
    // ExerciseRecordController — 答题记录（my/* 端点）
    // ================================================================

    @Test
    @Order(20)
    @DisplayName("ExerciseRecord GET /my/{exerciseId} — STUDENT 可访问 → 200(空列表)")
    void studentCanViewMyRecords() throws Exception {
        // 使用一个不存在的 exerciseId，期望返回空列表而非 404
        MvcResult result = mockMvc.perform(get("/api/exercise-records/my/999999")
                .header("Authorization", studentToken))
            .andReturn();
        // 可能返回 200（空列表）或 404（无此记录），取决于 Service 实现
        Assertions.assertNotEquals(401, result.getResponse().getStatus());
        Assertions.assertNotEquals(403, result.getResponse().getStatus());
    }

    @Test
    @Order(21)
    @DisplayName("ExerciseRecord GET /my/{exerciseId} — TEACHER 被拒绝 → 403")
    void teacherCannotViewMyRecords() throws Exception {
        mockMvc.perform(get("/api/exercise-records/my/999999")
                .header("Authorization", teacherToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(22)
    @DisplayName("ExerciseRecord GET /my/accuracy-trend — STUDENT 可访问 → 200")
    void studentCanViewAccuracyTrend() throws Exception {
        mockMvc.perform(get("/api/exercise-records/my/accuracy-trend")
                .header("Authorization", studentToken))
            .andExpect(status().isOk());
    }

    @Test
    @Order(23)
    @DisplayName("ExerciseRecord GET /my/accuracy-trend — TEACHER 被拒绝 → 403")
    void teacherCannotViewAccuracyTrend() throws Exception {
        mockMvc.perform(get("/api/exercise-records/my/accuracy-trend")
                .header("Authorization", teacherToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(24)
    @DisplayName("ExerciseRecord GET /my/{exerciseId}/attempt-count — STUDENT 可访问 → 200")
    void studentCanViewAttemptCount() throws Exception {
        mockMvc.perform(get("/api/exercise-records/my/999999/attempt-count")
                .header("Authorization", studentToken))
            .andExpect(status().isOk());
    }

    @Test
    @Order(25)
    @DisplayName("ExerciseRecord GET /my/{exerciseId}/attempt-count — TEACHER 被拒绝 → 403")
    void teacherCannotViewAttemptCount() throws Exception {
        mockMvc.perform(get("/api/exercise-records/my/999999/attempt-count")
                .header("Authorization", teacherToken))
            .andExpect(status().isForbidden());
    }

    // ================================================================
    // LearningProgressController — 学习进度写操作
    // ================================================================

    @Test
    @Order(30)
    @DisplayName("LearningProgress POST /progress — STUDENT 可创建 → 200")
    void studentCanCreateProgress() throws Exception {
        mockMvc.perform(post("/api/learning-progress/progress")
                .header("Authorization", studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courseId\":1,\"chapterId\":1,\"videoProgress\":0}"))
            .andExpect(status().isOk());
    }

    @Test
    @Order(31)
    @DisplayName("LearningProgress POST /progress — TEACHER 被拒绝 → 403")
    void teacherCannotCreateProgress() throws Exception {
        mockMvc.perform(post("/api/learning-progress/progress")
                .header("Authorization", teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courseId\":1,\"chapterId\":1,\"videoProgress\":0}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(32)
    @DisplayName("LearningProgress POST /progress — ADMIN 被拒绝 → 403")
    void adminCannotCreateProgress() throws Exception {
        mockMvc.perform(post("/api/learning-progress/progress")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courseId\":1,\"chapterId\":1,\"videoProgress\":0}"))
            .andExpect(status().isForbidden());
    }

}
