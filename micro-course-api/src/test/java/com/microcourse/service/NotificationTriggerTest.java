package com.microcourse.service;

import com.microcourse.BaseIntegrationTest;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.EnrollmentCreateRequest;
import com.microcourse.dto.EnrollmentVO;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.Notification;
import com.microcourse.enums.CourseStatus;
import com.microcourse.enums.NotificationType;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Phase B-2 (P0-7) 通知矩阵接线集成测试（需 DB）。
 *
 * <p>验证业务事件点是否真实触发 {@code @Async} 通知，并保证：
 * 1) 选课成功触发 ENROLLMENT_SUCCESS 通知；
 * 2) 课程审核通过触发 COURSE_APPROVED 通知；
 * 3) 通知失败/异常被隔离，绝不阻塞或破坏主业务链路。
 *
 * <p>测试直接调用 Service bean（绕过 HTTP 鉴权），异步采用轮询等待（避免固定 sleep 在慢机器上 flaky）。
 */
@DisplayName("P0-7 NotificationTrigger 集成")
class NotificationTriggerTest extends BaseIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime());

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EnrollmentService enrollmentService;
    @Autowired
    private CourseService courseService;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private EnrollmentRepository enrollmentRepository;

    // --------- helpers ---------

    private String uniq() {
        return Long.toString(System.nanoTime()) + "-" + SEQ.incrementAndGet();
    }

    private Long insertCategory() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO course_categories(name, level, sort_order, created_at, updated_at) " +
                "VALUES (?, 1, 0, now(), now()) RETURNING id",
                Long.class, "ncat-" + uniq());
    }

    private Long insertUser(String role) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO users(username, password, real_name, role, status, cas_bound, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 1, false, now(), now()) RETURNING id",
                Long.class, role.toLowerCase() + "-" + uniq(),
                "$2b$12$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuv",
                "通知测试" + role, role);
    }

    private Long insertCourse(Long categoryId, Long teacherId, int status) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO courses(title, category_id, teacher_id, status, is_free, price, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, true, 0, now(), now()) RETURNING id",
                Long.class, "ncourse-" + uniq(), categoryId, teacherId, status);
    }

    /** 以 ADMIN 身份写入 SecurityContext（approve/reject/publish 内部依赖 SecurityUtil）。 */
    private void loginAsAdmin(Long adminUserId) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                adminUserId, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /** 轮询等待指定用户收到指定类型通知，最多约 5 秒（异步线程提交独立事务）。 */
    private Notification awaitNotification(Long userId, NotificationType type) {
        for (int i = 0; i < 50; i++) {
            Notification n = notificationRepository.selectOne(
                    new LambdaQueryWrapper<Notification>()
                            .eq(Notification::getUserId, userId)
                            .eq(Notification::getType, type.getCode())
                            .last("LIMIT 1"));
            if (n != null) {
                return n;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return null;
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // --------- test 1 · 选课成功触发通知 ---------

    @Test
    @DisplayName("选课成功后异步发送 ENROLLMENT_SUCCESS 通知给学生")
    void shouldSendNotificationOnEnrollment() {
        Long catId = insertCategory();
        Long teacherId = insertUser("TEACHER");
        Long courseId = insertCourse(catId, teacherId, CourseStatus.PUBLISHED.getCode());
        Long studentId = insertUser("STUDENT");

        EnrollmentCreateRequest req = new EnrollmentCreateRequest();
        req.setUserId(studentId);
        req.setCourseId(courseId);
        req.setSourceChannel("WEB");

        EnrollmentVO vo = enrollmentService.enroll(req);
        assertNotNull(vo.getId(), "选课主流程应成功");

        Notification n = awaitNotification(studentId, NotificationType.ENROLLMENT_SUCCESS);
        assertNotNull(n, "选课成功应异步发送 ENROLLMENT_SUCCESS 通知");
        assertEquals(studentId, n.getUserId());
        assertEquals(NotificationType.ENROLLMENT_SUCCESS.getCode(), n.getType());
        assertEquals(courseId, n.getRelatedId(), "relatedId 应为 courseId");
        assertFalse(Boolean.TRUE.equals(n.getIsRead()), "新通知应为未读");
        assertTrue(n.getContent() != null && n.getContent().contains("成功选课"),
                "通知内容应包含选课成功文案");
    }

    // --------- test 2 · 课程审核通过触发通知 ---------

    @Test
    @DisplayName("课程审核通过后异步发送 COURSE_APPROVED 通知给教师")
    void shouldSendNotificationOnCourseApproval() {
        Long catId = insertCategory();
        Long teacherId = insertUser("TEACHER");
        // status=1=PENDING_REVIEW，满足 approve 的 CAS 前置
        Long courseId = insertCourse(catId, teacherId, CourseStatus.PENDING_REVIEW.getCode());
        Long adminId = insertUser("ADMIN");

        loginAsAdmin(adminId);
        courseService.approve(courseId);

        Notification n = awaitNotification(teacherId, NotificationType.COURSE_APPROVED);
        assertNotNull(n, "审核通过应异步发送 COURSE_APPROVED 通知给教师");
        assertEquals(teacherId, n.getUserId(), "通知接收者应为课程教师");
        assertEquals(courseId, n.getRelatedId(), "relatedId 应为 courseId");
    }

    // --------- test 3 · 通知失败隔离，不阻塞主流程 ---------

    @Test
    @DisplayName("通知异常被隔离：非法入参不抛出，选课主流程仍成功落库")
    void shouldNotBlockMainFlowWhenNotificationFails() {
        // 3.1 直接以非法入参调用，验证 @Async 实现内部异常隔离（不向调用方抛出）
        assertDoesNotThrow(
                () -> notificationService.notifyAsync(
                        null, NotificationType.ENROLLMENT_SUCCESS, "x", "y", 1L),
                "通知入参非法时不得抛出异常,以免影响主链路");

        // 3.2 端到端：即使通知是旁路调用，选课主流程必须成功并持久化
        Long catId = insertCategory();
        Long teacherId = insertUser("TEACHER");
        Long courseId = insertCourse(catId, teacherId, CourseStatus.PUBLISHED.getCode());
        Long studentId = insertUser("STUDENT");

        EnrollmentCreateRequest req = new EnrollmentCreateRequest();
        req.setUserId(studentId);
        req.setCourseId(courseId);
        req.setSourceChannel("WEB");

        EnrollmentVO vo = enrollmentService.enroll(req);
        assertNotNull(vo.getId(), "选课主流程不受通知影响,应成功返回");

        Enrollment persisted = enrollmentRepository.selectOne(
                new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getUserId, studentId)
                        .eq(Enrollment::getCourseId, courseId));
        assertNotNull(persisted, "选课记录应已持久化,与通知发送解耦");
    }
}
