package com.microcourse.controller;

import com.microcourse.BaseIntegrationTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 9 · 缺失数据表与 API 全维度集成测试
 *
 * <p>覆盖 8 个 P0 功能端点，4 角色矩阵 (ADMIN/TEACHER/STUDENT/ACADEMIC)：</p>
 * <ol>
 *   <li>POST /api/courses/{id}/reviews          — 提交评价</li>
 *   <li>GET  /api/courses/{id}/reviews          — 评价列表</li>
 *   <li>POST /api/courses/{id}/reviews/{rid}/approve — 审核通过</li>
 *   <li>POST /api/courses/{id}/reviews/{rid}/reject  — 审核驳回</li>
 *   <li>DELETE /api/courses/{id}/reviews/{rid}  — 删除评价</li>
 *   <li>GET  /api/admin/stats/overview          — 平台总览</li>
 *   <li>GET  /api/admin/stats/users             — 用户趋势</li>
 *   <li>GET  /api/admin/stats/courses           — 课程趋势</li>
 *   <li>GET  /api/admin/settings                — 获取系统设置</li>
 *   <li>PUT  /api/admin/settings                — 更新系统设置</li>
 *   <li>POST /api/users/batch                   — 批量导入</li>
 * </ol>
 *
 * <p>账号约定（p0-seed.sql + p1-academic-role-seed.sql）：</p>
 * <ul>
 *   <li>ADMIN:    admin / admin123（V1 种子，id=1）</li>
 *   <li>TEACHER:  p0_teacher / student123（p0-seed.sql，id=6）</li>
 *   <li>STUDENT:  student / student123（p0-seed.sql，id=7）</li>
 *   <li>ACADEMIC: academic_user / student123（p1-academic-role-seed.sql，id=100）</li>
 * </ul>
 */
@DisplayName("Phase 9 · 缺失数据表与 API 全维度集成测试")
@Sql(scripts = {"/sql/p0-seed.sql", "/sql/p1-academic-role-seed.sql"},
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class Phase9IntegrationTest extends BaseIntegrationTest {

    private static final String P0_PASSWORD = "student123";

    @Autowired
    private JdbcTemplate jdbc;

    private String teacherToken;
    private String studentToken;
    private String academicToken;

    private final List<Long> createdReviewIds = new ArrayList<>();
    private final List<Long> createdEnrollmentIds = new ArrayList<>();
    private final List<Long> createdProgressIds = new ArrayList<>();
    private Long isolatedCourseId;

    @BeforeEach
    void setupTokensAndData() throws Exception {
        teacherToken = "Bearer " + loginAs("p0_teacher", P0_PASSWORD);
        studentToken = "Bearer " + loginAs("student", P0_PASSWORD);
        academicToken = "Bearer " + loginAs("academic_user", P0_PASSWORD);
    }

    @AfterEach
    void cleanupData() {
        for (Long id : createdReviewIds) {
            try { jdbc.update("DELETE FROM course_reviews WHERE id = ?", id); } catch (Exception ignored) {}
        }
        for (Long id : createdProgressIds) {
            try { jdbc.update("DELETE FROM learning_progress WHERE id = ?", id); } catch (Exception ignored) {}
        }
        for (Long id : createdEnrollmentIds) {
            try { jdbc.update("DELETE FROM enrollments WHERE id = ?", id); } catch (Exception ignored) {}
        }
        if (isolatedCourseId != null) {
            try { jdbc.update("DELETE FROM course_review_logs WHERE course_id = ?", isolatedCourseId); } catch (Exception ignored) {}
            try { jdbc.update("DELETE FROM courses WHERE id = ?", isolatedCourseId); } catch (Exception ignored) {}
        }
        createdReviewIds.clear();
        createdEnrollmentIds.clear();
        createdProgressIds.clear();
    }

    /**
     * 插入一条 PUBLISHED(4) 隔离课程，返回 courseId。
     * 清理由 @AfterEach 统一处理。
     */
    private Long insertIsolatedCourse() {
        String uniqueTitle = "p9-course-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Long id = jdbc.queryForObject(
                "INSERT INTO courses (title, category_id, teacher_id, status, is_free, price, " +
                        "course_type, version, created_at, updated_at) " +
                        "VALUES (?, 1, 6, 4, TRUE, NULL, 'VIDEO', 0, NOW(), NOW()) RETURNING id",
                Long.class, uniqueTitle);
        isolatedCourseId = id;
        return id;
    }

    /**
     * 为 student(id=7) 在指定 course 创建选课记录 + 学习进度（video_progress >= 80）
     * 以通过 CourseReviewServiceImpl.create 的校验。
     */
    private Long setupEnrollmentWithProgress(Long courseId) {
        Long enrollmentId = jdbc.queryForObject(
                "INSERT INTO enrollments (course_id, user_id, enrollment_status, progress, completed, enrolled_at, updated_at) " +
                        "VALUES (?, ?, 'APPROVED', 0, FALSE, NOW(), NOW()) RETURNING id",
                Long.class, courseId, 7L);
        createdEnrollmentIds.add(enrollmentId);

        Long progressId = jdbc.queryForObject(
                "INSERT INTO learning_progress (user_id, course_id, chapter_id, video_progress, completed, created_at, updated_at) " +
                        "VALUES (?, ?, 1, 85, FALSE, NOW(), NOW()) RETURNING id",
                Long.class, 7L, courseId);
        createdProgressIds.add(progressId);

        return enrollmentId;
    }

    /**
     * 在 courseId 下插入一条 student(id=7) 的待审核评价（status=0），返回 reviewId。
     */
    private Long insertPendingReview(Long courseId) {
        Long id = jdbc.queryForObject(
                "INSERT INTO course_reviews (course_id, user_id, rating, content, is_anonymous, status, created_at, updated_at) " +
                        "VALUES (?, 7, 5, 'Phase9 test review', FALSE, 0, NOW(), NOW()) RETURNING id",
                Long.class, courseId);
        createdReviewIds.add(id);
        return id;
    }

    // =========================================================================
    // 1. POST /api/courses/{id}/reviews — 提交评价
    // =========================================================================

    @Test
    @DisplayName("[CR·POST] STUDENT 可提交评价（200）")
    void createReview_Student_Success() throws Exception {
        Long courseId = insertIsolatedCourse();
        setupEnrollmentWithProgress(courseId);

        mockMvc.perform(post("/api/courses/{id}/reviews", courseId)
                        .header("Authorization", studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":4,\"content\":\"很好的课程！\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").isNumber());
    }

    @Test
    @DisplayName("[CR·POST] TEACHER 提交评价 → 403")
    void createReview_Teacher_Forbidden() throws Exception {
        mockMvc.perform(post("/api/courses/1/reviews")
                        .header("Authorization", teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":4,\"content\":\"test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[CR·POST] ADMIN 提交评价 → 403")
    void createReview_Admin_Forbidden() throws Exception {
        mockMvc.perform(post("/api/courses/1/reviews")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":4,\"content\":\"test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[CR·POST] ACADEMIC 提交评价 → 403")
    void createReview_Academic_Forbidden() throws Exception {
        mockMvc.perform(post("/api/courses/1/reviews")
                        .header("Authorization", academicToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":4,\"content\":\"test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[CR·POST] 无 token → 401")
    void createReview_NoToken_Unauthorized() throws Exception {
        mockMvc.perform(post("/api/courses/1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":4,\"content\":\"test\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[CR·POST] 评分超出范围 → 400")
    void createReview_InvalidRating_BadRequest() throws Exception {
        Long courseId = insertIsolatedCourse();
        setupEnrollmentWithProgress(courseId);

        mockMvc.perform(post("/api/courses/{id}/reviews", courseId)
                        .header("Authorization", studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":6,\"content\":\"test\"}"))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // 2. GET /api/courses/{id}/reviews — 评价列表
    // =========================================================================

    @Test
    @DisplayName("[CR·GET] ADMIN 查看评价列表（200）")
    void listReviews_Admin_Success() throws Exception {
        mockMvc.perform(get("/api/courses/1/reviews")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("[CR·GET] STUDENT 查看评价列表（200）")
    void listReviews_Student_Success() throws Exception {
        mockMvc.perform(get("/api/courses/1/reviews")
                        .header("Authorization", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("[CR·GET] TEACHER 查看评价列表（200）")
    void listReviews_Teacher_Success() throws Exception {
        mockMvc.perform(get("/api/courses/1/reviews")
                        .header("Authorization", teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("[CR·GET] ACADEMIC 查看评价列表（200）")
    void listReviews_Academic_Success() throws Exception {
        mockMvc.perform(get("/api/courses/1/reviews")
                        .header("Authorization", academicToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("[CR·GET] 无 token → 401")
    void listReviews_NoToken_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/courses/1/reviews"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // 3. POST /api/courses/{id}/reviews/{rid}/approve — 审核通过
    // =========================================================================

    @Test
    @DisplayName("[CR·APPROVE] ADMIN 审核通过（200）")
    void approveReview_Admin_Success() throws Exception {
        Long courseId = insertIsolatedCourse();
        Long reviewId = insertPendingReview(courseId);

        mockMvc.perform(post("/api/courses/{id}/reviews/{rid}/approve", courseId, reviewId)
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("[CR·APPROVE] ACADEMIC 审核通过（200）")
    void approveReview_Academic_Success() throws Exception {
        Long courseId = insertIsolatedCourse();
        Long reviewId = insertPendingReview(courseId);

        mockMvc.perform(post("/api/courses/{id}/reviews/{rid}/approve", courseId, reviewId)
                        .header("Authorization", academicToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("[CR·APPROVE] STUDENT → 403")
    void approveReview_Student_Forbidden() throws Exception {
        Long courseId = insertIsolatedCourse();
        Long reviewId = insertPendingReview(courseId);

        mockMvc.perform(post("/api/courses/{id}/reviews/{rid}/approve", courseId, reviewId)
                        .header("Authorization", studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[CR·APPROVE] TEACHER → 403")
    void approveReview_Teacher_Forbidden() throws Exception {
        Long courseId = insertIsolatedCourse();
        Long reviewId = insertPendingReview(courseId);

        mockMvc.perform(post("/api/courses/{id}/reviews/{rid}/approve", courseId, reviewId)
                        .header("Authorization", teacherToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 4. POST /api/courses/{id}/reviews/{rid}/reject — 审核驳回
    // =========================================================================

    @Test
    @DisplayName("[CR·REJECT] ADMIN 审核驳回（200）")
    void rejectReview_Admin_Success() throws Exception {
        Long courseId = insertIsolatedCourse();
        Long reviewId = insertPendingReview(courseId);

        mockMvc.perform(post("/api/courses/{id}/reviews/{rid}/reject", courseId, reviewId)
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("[CR·REJECT] STUDENT → 403")
    void rejectReview_Student_Forbidden() throws Exception {
        Long courseId = insertIsolatedCourse();
        Long reviewId = insertPendingReview(courseId);

        mockMvc.perform(post("/api/courses/{id}/reviews/{rid}/reject", courseId, reviewId)
                        .header("Authorization", studentToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 5. DELETE /api/courses/{id}/reviews/{rid} — 删除评价
    // =========================================================================

    @Test
    @DisplayName("[CR·DELETE] ADMIN 删除评价（200）")
    void deleteReview_Admin_Success() throws Exception {
        Long courseId = insertIsolatedCourse();
        Long reviewId = insertPendingReview(courseId);

        mockMvc.perform(delete("/api/courses/{id}/reviews/{rid}", courseId, reviewId)
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("[CR·DELETE] ACADEMIC 删除评价（200）")
    void deleteReview_Academic_Success() throws Exception {
        Long courseId = insertIsolatedCourse();
        Long reviewId = insertPendingReview(courseId);

        mockMvc.perform(delete("/api/courses/{id}/reviews/{rid}", courseId, reviewId)
                        .header("Authorization", academicToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("[CR·DELETE] STUDENT → 403")
    void deleteReview_Student_Forbidden() throws Exception {
        Long courseId = insertIsolatedCourse();
        Long reviewId = insertPendingReview(courseId);

        mockMvc.perform(delete("/api/courses/{id}/reviews/{rid}", courseId, reviewId)
                        .header("Authorization", studentToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 6. GET /api/admin/stats/overview — 平台总览
    // =========================================================================

    @Test
    @DisplayName("[STATS·OVERVIEW] ADMIN 可查看（200）")
    void statsOverview_Admin_Success() throws Exception {
        mockMvc.perform(get("/api/admin/stats/overview")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalUsers").isNumber())
                .andExpect(jsonPath("$.data.totalCourses").isNumber());
    }

    @Test
    @DisplayName("[STATS·OVERVIEW] ACADEMIC 可查看（200）")
    void statsOverview_Academic_Success() throws Exception {
        mockMvc.perform(get("/api/admin/stats/overview")
                        .header("Authorization", academicToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalUsers").isNumber());
    }

    @Test
    @DisplayName("[STATS·OVERVIEW] STUDENT → 403")
    void statsOverview_Student_Forbidden() throws Exception {
        mockMvc.perform(get("/api/admin/stats/overview")
                        .header("Authorization", studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[STATS·OVERVIEW] TEACHER → 403")
    void statsOverview_Teacher_Forbidden() throws Exception {
        mockMvc.perform(get("/api/admin/stats/overview")
                        .header("Authorization", teacherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[STATS·OVERVIEW] 无 token → 401")
    void statsOverview_NoToken_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/stats/overview"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // 7. GET /api/admin/stats/users — 用户趋势
    // =========================================================================

    @Test
    @DisplayName("[STATS·USERS] ADMIN 可查看（200）")
    void statsUsers_Admin_Success() throws Exception {
        mockMvc.perform(get("/api/admin/stats/users")
                        .param("days", "7")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("[STATS·USERS] STUDENT → 403")
    void statsUsers_Student_Forbidden() throws Exception {
        mockMvc.perform(get("/api/admin/stats/users")
                        .header("Authorization", studentToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 8. GET /api/admin/stats/courses — 课程趋势
    // =========================================================================

    @Test
    @DisplayName("[STATS·COURSES] ADMIN 可查看（200）")
    void statsCourses_Admin_Success() throws Exception {
        mockMvc.perform(get("/api/admin/stats/courses")
                        .param("days", "7")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("[STATS·COURSES] STUDENT → 403")
    void statsCourses_Student_Forbidden() throws Exception {
        mockMvc.perform(get("/api/admin/stats/courses")
                        .header("Authorization", studentToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 9. GET /api/admin/settings — 获取系统设置
    // =========================================================================

    @Test
    @DisplayName("[SETTINGS·GET] ADMIN 可查看（200）")
    void getSettings_Admin_Success() throws Exception {
        mockMvc.perform(get("/api/admin/settings")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("[SETTINGS·GET] ACADEMIC 可查看（200）")
    void getSettings_Academic_Success() throws Exception {
        mockMvc.perform(get("/api/admin/settings")
                        .header("Authorization", academicToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("[SETTINGS·GET] STUDENT → 403")
    void getSettings_Student_Forbidden() throws Exception {
        mockMvc.perform(get("/api/admin/settings")
                        .header("Authorization", studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[SETTINGS·GET] TEACHER → 403")
    void getSettings_Teacher_Forbidden() throws Exception {
        mockMvc.perform(get("/api/admin/settings")
                        .header("Authorization", teacherToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 10. PUT /api/admin/settings — 更新系统设置
    // =========================================================================

    @Test
    @DisplayName("[SETTINGS·PUT] ADMIN 可更新（200）")
    void updateSettings_Admin_Success() throws Exception {
        mockMvc.perform(put("/api/admin/settings")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"settingKey\":\"platform.name\",\"settingValue\":\"测试平台\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("[SETTINGS·PUT] ACADEMIC → 403")
    void updateSettings_Academic_Forbidden() throws Exception {
        mockMvc.perform(put("/api/admin/settings")
                        .header("Authorization", academicToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"settingKey\":\"platform.name\",\"settingValue\":\"test\"}]"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[SETTINGS·PUT] STUDENT → 403")
    void updateSettings_Student_Forbidden() throws Exception {
        mockMvc.perform(put("/api/admin/settings")
                        .header("Authorization", studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"settingKey\":\"platform.name\",\"settingValue\":\"test\"}]"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[SETTINGS·PUT] TEACHER → 403")
    void updateSettings_Teacher_Forbidden() throws Exception {
        mockMvc.perform(put("/api/admin/settings")
                        .header("Authorization", teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"settingKey\":\"platform.name\",\"settingValue\":\"test\"}]"))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 11. POST /api/users/batch — 批量导入用户
    // =========================================================================

    @Test
    @DisplayName("[BATCH] ADMIN 批量导入（200 或 400）")
    void batchImport_Admin_Success() throws Exception {
        String csv = "username,password,realName,role,departmentName\n" +
                "batch_test1,Test1234,批量测试1,STUDENT,教务处\n";

        var result = mockMvc.perform(multipart("/api/users/batch")
                        .file("file", csv.getBytes())
                        .header("Authorization", bearerAdmin()))
                .andReturn().getResponse();

        int status = result.getStatus();
        assert status < 500 : "batchImport ADMIN 返回 " + status;
    }

    @Test
    @DisplayName("[BATCH] STUDENT → 403")
    void batchImport_Student_Forbidden() throws Exception {
        mockMvc.perform(multipart("/api/users/batch")
                        .file("file", "dummy".getBytes())
                        .header("Authorization", studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[BATCH] TEACHER → 403")
    void batchImport_Teacher_Forbidden() throws Exception {
        mockMvc.perform(multipart("/api/users/batch")
                        .file("file", "dummy".getBytes())
                        .header("Authorization", teacherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[BATCH] ACADEMIC → 403")
    void batchImport_Academic_Forbidden() throws Exception {
        mockMvc.perform(multipart("/api/users/batch")
                        .file("file", "dummy".getBytes())
                        .header("Authorization", academicToken))
                .andExpect(status().isForbidden());
    }
}
