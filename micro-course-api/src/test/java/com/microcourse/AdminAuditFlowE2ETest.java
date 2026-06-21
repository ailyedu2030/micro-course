package com.microcourse;

import com.microcourse.enums.CourseStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理员审核流 E2E 测试（Round 10-4）
 *
 * <p>背景：经再发现，管理员审核流（查看待审 → 审核通过/驳回 → 通知教师 → 发布/下架 → 统计/日志）
 * 此前<b>零端到端测试</b>，任何对审核流程的修改都没有回归保护。本类补齐该缺口。</p>
 *
 * <p>实现说明（严格对齐既有测试基础设施，<b>零业务代码修改</b>）：</p>
 * <ul>
 *   <li>沿用项目既有的 MockMvc + {@code @Sql(p0-seed.sql)} 模式（见 NotificationFlowIntegrationTest）。
 *       任务原始骨架中的 {@code TestRestTemplate} 与 {@code com.microcourse.util.TestHelper.authEntity}
 *       在本仓库并不存在（真实 {@code TestHelper} 位于 {@code com.microcourse.support} 且签名不同），
 *       故改用真实可编译、可通过的基础设施。</li>
 *   <li>种子口令以实际 fixture 为准：admin/admin123、p0_teacher(id=6)/<b>student123</b>
 *       （p0-seed 中 teacher 与 student 复用同一 bcrypt 哈希，全套既有测试均以 student123 登录 p0_teacher）。</li>
 *   <li>每个用例<b>自包含、相互独立</b>：通过 JdbcTemplate 即时插入所需状态的课程（teacher_id=6），
 *       调用真实 HTTP 端点，断言 HTTP 状态 + JSON + DB 落库，{@code @AfterEach} 按 ID 定向清理，
 *       不依赖测试执行顺序，不污染既有 153 测试。</li>
 * </ul>
 *
 * <p>状态机（CourseStatus）：DRAFT(0) → PENDING_REVIEW(1) → APPROVED(2)/REJECTED(3) → PUBLISHED(4) → CLOSED(5)。</p>
 */
@DisplayName("Round 10-4 · 管理员审核流 E2E")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Sql(scripts = "/sql/p0-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AdminAuditFlowE2ETest extends BaseIntegrationTest {

    /** p0-seed 教师账号（courses.teacher_id），同时是审核通过/驳回后异步通知的接收者。 */
    private static final long TEACHER_ID = 6L;
    private static final String TEACHER_PASSWORD = "student123";
    /** p0-seed 课程分类（courses.category_id 的 NOT NULL FK）。 */
    private static final long CATEGORY_ID = 1L;

    @Autowired
    private JdbcTemplate jdbc;

    /** 本类测试期间动态创建的课程 ID，用于 @AfterEach 定向清理（含其审核日志与衍生通知）。 */
    private final List<Long> createdCourseIds = new ArrayList<>();

    // =====================================================================
    // 辅助方法
    // =====================================================================

    /** 直接落库一条课程（teacher_id=6, category_id=1, 免费），返回自增 ID 并登记待清理。 */
    private long insertCourse(int status) {
        Long id = jdbc.queryForObject(
                "INSERT INTO courses(title, category_id, teacher_id, status, is_free, price, "
                        + "created_at, updated_at) VALUES (?, ?, ?, ?, true, 0, now(), now()) RETURNING id",
                Long.class, "audit-e2e-" + System.nanoTime(), CATEGORY_ID, TEACHER_ID, status);
        createdCourseIds.add(id);
        return id;
    }

    private int courseStatus(long courseId) {
        Integer s = jdbc.queryForObject(
                "SELECT status FROM courses WHERE id = ?", Integer.class, courseId);
        return s == null ? -1 : s;
    }

    private long reviewLogCount(long courseId, String action) {
        Long c = jdbc.queryForObject(
                "SELECT count(*) FROM course_review_logs WHERE course_id = ? AND action = ?",
                Long.class, courseId, action);
        return c == null ? 0L : c;
    }

    /** 轮询等待指定用户收到指定类型且 related_id 匹配的通知（@Async 独立事务），最多约 6 秒。 */
    private boolean awaitNotification(long userId, String type, long relatedId) throws InterruptedException {
        for (int i = 0; i < 60; i++) {
            Long c = jdbc.queryForObject(
                    "SELECT count(*) FROM notifications WHERE user_id = ? AND type = ? AND related_id = ?",
                    Long.class, userId, type, relatedId);
            if (c != null && c > 0) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }

    private String teacherBearer() throws Exception {
        return "Bearer " + loginAs("p0_teacher", TEACHER_PASSWORD);
    }

    @AfterEach
    void cleanupCreatedCourses() {
        for (Long id : createdCourseIds) {
            try { jdbc.update("DELETE FROM notifications WHERE related_id = ? AND type LIKE 'COURSE_%'", id); } catch (Exception ignored) {}
            try { jdbc.update("DELETE FROM course_review_logs WHERE course_id = ?", id); } catch (Exception ignored) {}
            try { jdbc.update("DELETE FROM courses WHERE id = ?", id); } catch (Exception ignored) {}
        }
        createdCourseIds.clear();
    }

    // =====================================================================
    // 用例
    // =====================================================================

    @Test
    @Order(1)
    @DisplayName("1·管理员查看待审核课程 → 200")
    void shouldViewPendingReviewCourses() throws Exception {
        mockMvc.perform(get("/api/courses/pending-review")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @Order(2)
    @DisplayName("2·教师不能查看待审核列表 → 403")
    void shouldNotAllowTeacherToViewPendingReview() throws Exception {
        mockMvc.perform(get("/api/courses/pending-review")
                        .header("Authorization", teacherBearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    @DisplayName("3·审核通过 → 状态 APPROVED + 审核日志写入")
    void shouldApprovePendingCourse() throws Exception {
        long courseId = insertCourse(CourseStatus.PENDING_REVIEW.getCode());

        mockMvc.perform(post("/api/courses/" + courseId + "/approve")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals(CourseStatus.APPROVED.getCode(), courseStatus(courseId),
                "审核通过后课程状态应为 APPROVED(2)");
        assertTrue(reviewLogCount(courseId, "APPROVE") >= 1,
                "course_review_logs 应写入一条 APPROVE 审核记录");
    }

    @Test
    @Order(4)
    @DisplayName("4·驳回带原因 → 状态 REJECTED + 原因落库（≥5字符）")
    void shouldRejectPendingCourseWithReason() throws Exception {
        long courseId = insertCourse(CourseStatus.PENDING_REVIEW.getCode());
        String reason = "课程内容存在事实性错误，需修订后再提交";
        assertTrue(reason.length() >= 5, "驳回原因长度应满足 ≥5 字符约束");

        mockMvc.perform(post("/api/courses/" + courseId + "/reject")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + reason + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals(CourseStatus.REJECTED.getCode(), courseStatus(courseId),
                "驳回后课程状态应为 REJECTED(3)");
        String persisted = jdbc.queryForObject(
                "SELECT reject_reason FROM courses WHERE id = ?", String.class, courseId);
        assertEquals(reason, persisted, "驳回原因应原样落库到 courses.reject_reason");
        assertTrue(reviewLogCount(courseId, "REJECT") >= 1,
                "course_review_logs 应写入一条 REJECT 审核记录");
    }

    @Test
    @Order(5)
    @DisplayName("5·驳回原因过短 → 400，状态不变")
    void shouldRejectReasonTooShort() throws Exception {
        long courseId = insertCourse(CourseStatus.PENDING_REVIEW.getCode());

        mockMvc.perform(post("/api/courses/" + courseId + "/reject")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"no\"}"))
                .andExpect(status().isBadRequest());

        assertEquals(CourseStatus.PENDING_REVIEW.getCode(), courseStatus(courseId),
                "原因校验失败时不得改变课程状态（仍为 PENDING_REVIEW）");
    }

    @Test
    @Order(6)
    @DisplayName("6·审核通过后异步通知教师 COURSE_APPROVED")
    void shouldNotifyTeacherOnApproval() throws Exception {
        jdbc.update("DELETE FROM notifications WHERE user_id = ? AND type = 'COURSE_APPROVED'", TEACHER_ID);
        long courseId = insertCourse(CourseStatus.PENDING_REVIEW.getCode());

        mockMvc.perform(post("/api/courses/" + courseId + "/approve")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk());

        assertTrue(awaitNotification(TEACHER_ID, "COURSE_APPROVED", courseId),
                "审核通过后教师应异步收到 COURSE_APPROVED 通知（related_id=courseId）");

        // 教师登录通过 API 查通知，验证收件端可见
        mockMvc.perform(get("/api/notifications?type=COURSE_APPROVED")
                        .header("Authorization", teacherBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value("COURSE_APPROVED"))
                .andExpect(jsonPath("$.data.items[0].relatedId").value((int) courseId));
    }

    @Test
    @Order(7)
    @DisplayName("7·驳回后异步通知教师 COURSE_REJECTED")
    void shouldNotifyTeacherOnRejection() throws Exception {
        jdbc.update("DELETE FROM notifications WHERE user_id = ? AND type = 'COURSE_REJECTED'", TEACHER_ID);
        long courseId = insertCourse(CourseStatus.PENDING_REVIEW.getCode());

        mockMvc.perform(post("/api/courses/" + courseId + "/reject")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"封面与简介不符，请修改后重新提交\"}"))
                .andExpect(status().isOk());

        assertTrue(awaitNotification(TEACHER_ID, "COURSE_REJECTED", courseId),
                "驳回后教师应异步收到 COURSE_REJECTED 通知（related_id=courseId）");

        mockMvc.perform(get("/api/notifications?type=COURSE_REJECTED")
                        .header("Authorization", teacherBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value("COURSE_REJECTED"))
                .andExpect(jsonPath("$.data.items[0].relatedId").value((int) courseId));
    }

    @Test
    @Order(8)
    @DisplayName("8·发布已通过课程 → APPROVED → PUBLISHED")
    void shouldPublishApprovedCourse() throws Exception {
        long courseId = insertCourse(CourseStatus.APPROVED.getCode());

        mockMvc.perform(post("/api/courses/" + courseId + "/publish")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals(CourseStatus.PUBLISHED.getCode(), courseStatus(courseId),
                "发布后课程状态应为 PUBLISHED(4)");
        Long publishedAtCount = jdbc.queryForObject(
                "SELECT count(*) FROM courses WHERE id = ? AND published_at IS NOT NULL",
                Long.class, courseId);
        assertEquals(1L, publishedAtCount, "发布后 published_at 应被写入");
    }

    @Test
    @Order(9)
    @DisplayName("9·教师不能发布课程 → 403（仅 ADMIN）")
    void shouldNotPublishByTeacher() throws Exception {
        long courseId = insertCourse(CourseStatus.APPROVED.getCode());

        mockMvc.perform(post("/api/courses/" + courseId + "/publish")
                        .header("Authorization", teacherBearer()))
                .andExpect(status().isForbidden());

        assertEquals(CourseStatus.APPROVED.getCode(), courseStatus(courseId),
                "教师越权发布被拦截后，课程状态应保持 APPROVED(2) 不变");
    }

    @Test
    @Order(10)
    @DisplayName("10·不能审核非 PENDING 课程 → PUBLISHED.approve → 400")
    void shouldNotApproveNonPendingCourse() throws Exception {
        long courseId = insertCourse(CourseStatus.PUBLISHED.getCode());

        mockMvc.perform(post("/api/courses/" + courseId + "/approve")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isBadRequest());

        assertEquals(CourseStatus.PUBLISHED.getCode(), courseStatus(courseId),
                "对非 PENDING 课程审核应被状态机拒绝，状态保持 PUBLISHED(4)");
    }

    @Test
    @Order(11)
    @DisplayName("11·下架已发布课程 → PUBLISHED → CLOSED")
    void shouldUnpublishPublishedCourse() throws Exception {
        long courseId = insertCourse(CourseStatus.PUBLISHED.getCode());

        mockMvc.perform(post("/api/courses/" + courseId + "/unpublish")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals(CourseStatus.CLOSED.getCode(), courseStatus(courseId),
                "下架后课程状态应为 CLOSED(5)");
    }

    @Test
    @Order(12)
    @DisplayName("12·课程审核日志完整记录（reviewer/前后状态）")
    void shouldViewCourseReviewLogs() throws Exception {
        long courseId = insertCourse(CourseStatus.PENDING_REVIEW.getCode());

        mockMvc.perform(post("/api/courses/" + courseId + "/approve")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk());

        // 当前未暴露 GET /api/courses/{id}/review-logs 端点，按审计落库直接核验审核轨迹字段。
        Long rows = jdbc.queryForObject(
                "SELECT count(*) FROM course_review_logs WHERE course_id = ? AND action = 'APPROVE' "
                        + "AND previous_status = ? AND new_status = ? AND reviewer_id = 1",
                Long.class, courseId,
                CourseStatus.PENDING_REVIEW.getCode(), CourseStatus.APPROVED.getCode());
        assertEquals(1L, rows,
                "审核日志应记录 action=APPROVE、previous=PENDING(1)、new=APPROVED(2)、reviewer=admin(1)");
    }

    @Test
    @Order(13)
    @DisplayName("13·查看平台总览 → 用户/课程统计")
    void shouldViewPlatformDashboard() throws Exception {
        mockMvc.perform(get("/api/admin/stats/overview")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalUsers").isNumber())
                .andExpect(jsonPath("$.data.totalCourses").isNumber());
    }

    @Test
    @Order(14)
    @DisplayName("14·查看操作日志 → 200")
    void shouldViewOperationLogs() throws Exception {
        mockMvc.perform(get("/api/operation-logs")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @Order(15)
    @DisplayName("15·完整审核流验证：PENDING → APPROVED → PUBLISHED → CLOSED")
    void adminAuditFlowIsComplete() throws Exception {
        long courseId = insertCourse(CourseStatus.PENDING_REVIEW.getCode());
        String admin = bearerAdmin();

        // 审核通过
        mockMvc.perform(post("/api/courses/" + courseId + "/approve")
                        .header("Authorization", admin))
                .andExpect(status().isOk());
        assertEquals(CourseStatus.APPROVED.getCode(), courseStatus(courseId));

        // 发布
        mockMvc.perform(post("/api/courses/" + courseId + "/publish")
                        .header("Authorization", admin))
                .andExpect(status().isOk());
        assertEquals(CourseStatus.PUBLISHED.getCode(), courseStatus(courseId));

        // 下架
        mockMvc.perform(post("/api/courses/" + courseId + "/unpublish")
                        .header("Authorization", admin))
                .andExpect(status().isOk());
        assertEquals(CourseStatus.CLOSED.getCode(), courseStatus(courseId));

        // 审核轨迹：APPROVE + PUBLISH 各 1 条
        assertTrue(reviewLogCount(courseId, "APPROVE") >= 1, "应留有 APPROVE 审核日志");
        assertTrue(reviewLogCount(courseId, "PUBLISH") >= 1, "应留有 PUBLISH 审核日志");
    }
}
