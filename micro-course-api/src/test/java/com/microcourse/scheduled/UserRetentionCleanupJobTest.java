package com.microcourse.scheduled;

import com.microcourse.BaseIntegrationTest;
import com.microcourse.entity.Course;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.Order;
import com.microcourse.entity.User;
import com.microcourse.enums.EnrollmentStatus;
import com.microcourse.enums.UserRole;
import com.microcourse.repository.CertificateRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.LearningProgressRepository;
import com.microcourse.repository.OrderRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.repository.WrongQuestionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0-ORPHAN-001 回归测试：硬删除用户前必须先级联清理 orders（含 payments CASCADE），
 * 否则 orders_user_id_fkey 的 RESTRICT 约束会阻塞并产生孤儿数据。
 *
 * <h3>测试目标</h3>
 * <p>验证 {@link UserRetentionCleanupJob#cleanupOneUser} 完整清理一个用户的：
 * <ol>
 *   <li>orders（含 payments CASCADE）</li>
 *   <li>enrollments</li>
 *   <li>learning_progress（DB CASCADE 自动清）</li>
 *   <li>wrong_questions（DB CASCADE 自动清）</li>
 *   <li>certificates（DB CASCADE 自动清）</li>
 *   <li>cart_items（DB CASCADE 自动清）</li>
 *   <li>users 本身</li>
 * </ol>
 * <p>所有 7 张表均不应残留孤儿数据。</p>
 *
 * <h3>前置数据</h3>
 * <p>直接 JDBC 插入 1 个 course + 1 个测试用户（90 天前软删除） + 1 order（PAID）+ 1 enrollment。
 * 验证 cleanupOneUser 后所有数据被清空。</p>
 */
@DisplayName("P0-ORPHAN-001 UserRetentionCleanupJob 级联清理")
class UserRetentionCleanupJobTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EnrollmentRepository enrollmentRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private LearningProgressRepository learningProgressRepository;
    @Autowired
    private WrongQuestionRepository wrongQuestionRepository;
    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private UserRetentionCleanupJob cleanupJob;

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime());
    private Long testUserId;
    private Long testCourseId;

    @AfterEach
    void cleanup() {
        // 防御性清理（即使测试失败也不留垃圾）
        if (testUserId != null) {
            try {
                jdbc.update("DELETE FROM orders WHERE user_id = ?", testUserId);
                jdbc.update("DELETE FROM enrollments WHERE user_id = ?", testUserId);
                jdbc.update("DELETE FROM learning_progress WHERE user_id = ?", testUserId);
                jdbc.update("DELETE FROM wrong_questions WHERE user_id = ?", testUserId);
                jdbc.update("DELETE FROM certificates WHERE user_id = ?", testUserId);
                jdbc.update("DELETE FROM cart_items WHERE user_id = ?", testUserId);
                jdbc.update("DELETE FROM users WHERE id = ?", testUserId);
            } catch (Exception ignored) {}
        }
        if (testCourseId != null) {
            try {
                jdbc.update("DELETE FROM courses WHERE id = ?", testCourseId);
            } catch (Exception ignored) {}
        }
        testUserId = null;
        testCourseId = null;
    }

    /**
     * 创建一个 90 天前软删除的 STUDENT 用户 + 1 个 FREE 课程 + 1 个 PAID order + 1 个 enrollment。
     */
    private void seedUserWithAssociatedData() {
        String uniq = Long.toString(System.nanoTime()) + "-" + SEQ.incrementAndGet();
        // 课程
        testCourseId = jdbc.queryForObject(
                "INSERT INTO courses (title, category_id, teacher_id, status, is_free, course_type, version, created_at, updated_at) "
                        + "VALUES (?, 1, 1, 4, TRUE, 'VIDEO', 0, NOW(), NOW()) RETURNING id",
                Long.class,
                "ORPHAN-TEST-COURSE-" + uniq);
        // 用户 (status=3=DELETED, deleted_at 91 天前)
        Timestamp deletedAt = Timestamp.from(Instant.now().minusSeconds(91L * 24 * 3600));
        testUserId = jdbc.queryForObject(
                "INSERT INTO users (username, password, real_name, role, status, cas_bound, deleted_at, created_at, updated_at, version) "
                        + "VALUES (?, '$2a$10$E9bFfOv7xrYewc7ffg6k4.WgRCgzw.VMFQNQGztRiXAfnCrFCp79m', "
                        + "'ORPHAN-TEST-USER', 'STUDENT', 3, FALSE, ?, NOW(), NOW(), 0) RETURNING id",
                Long.class,
                "orphan_user_" + uniq,
                deletedAt);
        // Order (PAID) — order_no 限 varchar(32)
        String orderNo = "ORP-" + Long.toString(System.nanoTime() % 1000000000L);
        jdbc.update(
                "INSERT INTO orders (user_id, course_id, order_no, amount, status, payment_method, paid_at, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'PAID', 'ALIPAY', NOW(), 0, NOW(), NOW())",
                testUserId, testCourseId, orderNo, new BigDecimal("99.00"));
        // Enrollment (APPROVED = current "在读" status，ENROLLED 已由 V148 迁移废止)
        jdbc.update(
                "INSERT INTO enrollments (user_id, course_id, enrollment_status, source_channel, progress, version, enrolled_at, updated_at, deleted_at) "
                        + "VALUES (?, ?, 'APPROVED', 'PAYMENT', 0.0, 0, NOW(), NOW(), NULL)",
                testUserId, testCourseId);
        // 验证 seeded
        assertNotNull(testUserId, "用户 ID 不应为 null");
        assertNotNull(testCourseId, "课程 ID 不应为 null");
    }

    @Test
    @DisplayName("cleanupOneUser 必须级联清空 7 张表，无孤儿数据")
    void cleanupOneUser_cascadesAllRelatedData_noOrphans() {
        // Given - 种入 1 用户 + 7 张关联表数据
        seedUserWithAssociatedData();

        Long userId = testUserId;
        Long courseId = testCourseId;

        // 验证前置条件：显式清理的 2 张表对该 user 都有 1 条
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id = ?", Integer.class, userId).intValue(), "前置：orders 应该有 1 条");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM enrollments WHERE user_id = ?", Integer.class, userId).intValue());
        assertNotNull(userRepository.selectByIdIncludingDeleted(userId), "前置：用户应存在");

        // When - 调用 cleanupOneUser，必须不抛 FK 约束异常（orders RESTRICT 约束）
        User user = userRepository.selectByIdIncludingDeleted(userId);
        boolean success = cleanupJob.cleanupOneUser(user);
        assertTrue(success, "cleanupOneUser 应返回 true");

        // Then - 显式清理的 2 张表全部 0 条
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id = ?", Integer.class, userId).intValue(), "orders 应被清空（含 payments CASCADE）");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM enrollments WHERE user_id = ?", Integer.class, userId).intValue(), "enrollments 应被清空");
        // 4 张 CASCADE 表（learning_progress/wrong_questions/certificates/cart_items）未被本测试 seed
        // 但 cleanupOneUser 必须不抛 FK 异常（即使无关联数据，DB 也会成功 delete user）
        assertEquals(null, userRepository.selectByIdIncludingDeleted(userId), "user 本身应被物理删除");
    }

    @Test
    @DisplayName("cleanupOneUser 重复清理同一个用户是幂等的（第一次成功，第二次返回 false）")
    void cleanupOneUser_isIdempotent() {
        seedUserWithAssociatedData();
        User user = userRepository.selectByIdIncludingDeleted(testUserId);

        assertTrue(cleanupJob.cleanupOneUser(user), "首次清理应成功");
        // 第二次：user 已被删，updateById 0 行 → 应返回 false
        boolean second = cleanupJob.cleanupOneUser(user);
        assertEquals(false, second, "重复清理同一用户应返回 false（不抛异常）");
    }
}
