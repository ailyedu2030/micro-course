package com.microcourse.service;

import com.microcourse.BaseIntegrationTest;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.EnrollmentCreateRequest;
import com.microcourse.dto.EnrollmentUpdateRequest;
import com.microcourse.dto.EnrollmentVO;
import com.microcourse.entity.EnrollmentHistory;
import com.microcourse.enums.CourseStatus;
import com.microcourse.enums.EnrollmentStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.EnrollmentHistoryRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.service.EnrollmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * P0-2 选课状态机集成测试（需 DB）。
 * 验证：课程 PUBLISHED 校验 / 状态转换白名单 / 审计历史写入。
 */
@DisplayName("P0-2 EnrollmentStatusMachine 集成")
class EnrollmentStatusMachineTest extends BaseIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime());

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EnrollmentService enrollmentService;
    @Autowired
    private EnrollmentRepository enrollmentRepository;
    @Autowired
    private EnrollmentHistoryRepository enrollmentHistoryRepository;

    // --------- helpers ---------

    private String uniq() {
        return Long.toString(System.nanoTime()) + "-" + SEQ.incrementAndGet();
    }

    private Long insertCategory() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO course_categories(name, level, sort_order, created_at, updated_at) " +
                "VALUES (?, 1, 0, now(), now()) RETURNING id",
                Long.class, "tcat-" + uniq());
    }

    private Long insertUser(String role) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO users(username, password, real_name, role, status, cas_bound, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 1, false, now(), now()) RETURNING id",
                Long.class, role.toLowerCase() + "-" + uniq(),
                "$2b$12$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuv",
                "测试" + role, role);
    }

    private Long insertCourse(Long categoryId, Long teacherId, int status) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO courses(title, category_id, teacher_id, status, is_free, price, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, true, 0, now(), now()) RETURNING id",
                Long.class, "tcourse-" + uniq(), categoryId, teacherId, status);
    }

    /** 旁路插入选课记录（状态由参数指定），仅用于测试非法转换/有效转换场景 */
    private Long insertEnrollment(Long userId, Long courseId, String status) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO enrollments(course_id, user_id, progress, completed, enrollment_status, enrolled_at, updated_at) " +
                "VALUES (?, ?, 0, false, ?, now(), now()) RETURNING id",
                Long.class, courseId, userId, status);
    }

    // --------- test 1 ---------

    @Test
    @DisplayName("拒绝为非 PUBLISHED 课程选课")
    void shouldRejectEnrollForNonPublishedCourse() {
        Long catId = insertCategory();
        Long teacherId = insertUser("TEACHER");
        Long courseId = insertCourse(catId, teacherId, CourseStatus.DRAFT.getCode()); // DRAFT=0
        Long studentId = insertUser("STUDENT");

        EnrollmentCreateRequest req = new EnrollmentCreateRequest();
        req.setUserId(studentId);
        req.setCourseId(courseId);
        req.setSourceChannel("WEB");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> enrollmentService.enroll(req));
        assertEquals(ErrorCode.COURSE_NOT_PUBLISHED.getCode(), ex.getCode());
    }

    // --------- test 2 ---------

    @Test
    @DisplayName("拒绝非法状态转换：CANCELLED → COMPLETED")
    void shouldRejectIllegalStatusTransition() {
        Long catId = insertCategory();
        Long teacherId = insertUser("TEACHER");
        Long courseId = insertCourse(catId, teacherId, CourseStatus.PUBLISHED.getCode());
        Long studentId = insertUser("STUDENT");
        Long enrollId = insertEnrollment(studentId, courseId,
                EnrollmentStatus.CANCELLED.getValue());

        EnrollmentUpdateRequest req = new EnrollmentUpdateRequest();
        req.setEnrollmentStatus(EnrollmentStatus.COMPLETED.getValue());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> enrollmentService.updateEnrollment(enrollId, req));
        assertEquals(ErrorCode.INVALID_STATUS_TRANSITION.getCode(), ex.getCode());
    }

    // --------- test 3 ---------

    @Test
    @DisplayName("合法状态转换：PENDING → APPROVED → COMPLETED")
    void shouldAllowValidStatusTransition() {
        // 枚举层面断言
        assertTrue(EnrollmentStatus.PENDING.canTransitionTo(EnrollmentStatus.APPROVED));
        assertTrue(EnrollmentStatus.APPROVED.canTransitionTo(EnrollmentStatus.COMPLETED));

        // 服务层面
        Long catId = insertCategory();
        Long teacherId = insertUser("TEACHER");
        Long courseId = insertCourse(catId, teacherId, CourseStatus.PUBLISHED.getCode());
        Long studentId = insertUser("STUDENT");
        Long enrollId = insertEnrollment(studentId, courseId, EnrollmentStatus.PENDING.getValue());

        EnrollmentUpdateRequest r1 = new EnrollmentUpdateRequest();
        r1.setEnrollmentStatus(EnrollmentStatus.APPROVED.getValue());
        EnrollmentVO vo1 = enrollmentService.updateEnrollment(enrollId, r1);
        assertEquals(EnrollmentStatus.APPROVED.getValue(), vo1.getEnrollmentStatus());

        EnrollmentUpdateRequest r2 = new EnrollmentUpdateRequest();
        r2.setEnrollmentStatus(EnrollmentStatus.COMPLETED.getValue());
        EnrollmentVO vo2 = enrollmentService.updateEnrollment(enrollId, r2);
        assertEquals(EnrollmentStatus.COMPLETED.getValue(), vo2.getEnrollmentStatus());
    }

    // --------- test 4 ---------

    @Test
    @DisplayName("选课后 enrollment_histories 写入审计记录")
    void shouldWriteEnrollmentHistoryOnEnroll() {
        Long catId = insertCategory();
        Long teacherId = insertUser("TEACHER");
        Long courseId = insertCourse(catId, teacherId, CourseStatus.PUBLISHED.getCode());
        Long studentId = insertUser("STUDENT");

        EnrollmentCreateRequest req = new EnrollmentCreateRequest();
        req.setUserId(studentId);
        req.setCourseId(courseId);
        req.setSourceChannel("WEB");

        EnrollmentVO vo = enrollmentService.enroll(req);
        assertNotNull(vo.getId());

        // 对外字符串值保持 "ENROLLED"（前端无感）
        assertEquals(EnrollmentStatus.LEGACY_ENROLLED_VALUE, vo.getEnrollmentStatus(),
                "enroll() 对外状态值必须保持 ENROLLED 以兼容前端");

        List<EnrollmentHistory> histories = enrollmentHistoryRepository.selectList(
                new LambdaQueryWrapper<EnrollmentHistory>()
                        .eq(EnrollmentHistory::getEnrollmentId, vo.getId()));
        assertEquals(1, histories.size(), "选课成功后应写入 1 条审计记录");
        EnrollmentHistory h = histories.get(0);
        assertEquals(EnrollmentStatus.APPROVED.getValue(), h.getNewStatus(),
                "审计表 new_status 应为契约规范值 APPROVED");
        assertNull(h.getPreviousStatus(), "首次选课无 previous 状态");
        assertTrue(h.getReason() != null && h.getReason().contains("ENROLL"),
                "reason 应包含 ENROLL 动作标识");
    }
}
