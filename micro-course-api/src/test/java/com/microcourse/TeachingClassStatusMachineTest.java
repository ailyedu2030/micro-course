package com.microcourse;

import com.microcourse.dto.TeachingClassCreateRequest;
import com.microcourse.dto.TeachingClassVO;
import com.microcourse.enums.TeachingClassStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.service.TeachingClassService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Round 6 教学班状态机集成测试（需 DB）。
 * 验证：创建默认状态 ACTIVE / 结课流转 / 停开流转 / 终态拒绝 / 停开原因必填。
 * 直接调用 TeachingClassService（complete/cancel/create 均不依赖 SecurityContext，
 * operatorId 以参数传入），与 EnrollmentStatusMachineTest 同款范式。
 */
@DisplayName("Round 6 TeachingClassStatusMachine 集成")
class TeachingClassStatusMachineTest extends BaseIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime());

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TeachingClassService teachingClassService;

    // --------- helpers ---------

    private String uniq() {
        return Long.toString(System.nanoTime()) + "-" + SEQ.incrementAndGet();
    }

    private Long insertCategory() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO course_categories(name, level, sort_order, created_at, updated_at) " +
                "VALUES (?, 1, 0, now(), now()) RETURNING id",
                Long.class, "tccat-" + uniq());
    }

    private Long insertTeacher() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO users(username, password, real_name, role, status, cas_bound, created_at, updated_at) " +
                "VALUES (?, ?, ?, 'TEACHER', 1, false, now(), now()) RETURNING id",
                Long.class, "tcteacher-" + uniq(),
                "$2b$12$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuv", "测试教师");
    }

    private Long insertCourse(Long categoryId, Long teacherId) {
        // course status 任意（create 教学班不校验课程状态）；用 4(PUBLISHED) 对齐种子风格
        return jdbcTemplate.queryForObject(
                "INSERT INTO courses(title, category_id, teacher_id, status, is_free, price, created_at, updated_at) " +
                "VALUES (?, ?, ?, 4, true, 0, now(), now()) RETURNING id",
                Long.class, "tccourse-" + uniq(), categoryId, teacherId);
    }

    /** 旁路插入教学班，状态由参数指定（用于构造各转换场景的前置状态）。 */
    private Long insertTeachingClass(Long courseId, Long teacherId, int status) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO teaching_classes(course_id, teacher_id, name, max_students, student_count, status, created_at, updated_at, version) " +
                "VALUES (?, ?, ?, 50, 0, ?, now(), now(), 0) RETURNING id",
                Long.class, courseId, teacherId, "教学班-" + uniq(), status);
    }

    private Long newClassWithStatus(int status) {
        Long teacherId = insertTeacher();
        Long catId = insertCategory();
        Long courseId = insertCourse(catId, teacherId);
        return insertTeachingClass(courseId, teacherId, status);
    }

    // --------- tests ---------

    @Test
    @DisplayName("结课：ACTIVE → COMPLETED 成功")
    void completeActiveClassShouldSucceed() {
        Long classId = newClassWithStatus(TeachingClassStatus.ACTIVE.getCode());
        teachingClassService.complete(classId, 1L);
        TeachingClassVO vo = teachingClassService.getById(classId);
        assertEquals(TeachingClassStatus.COMPLETED.getCode(), vo.getStatus());
    }

    @Test
    @DisplayName("结课：COMPLETED 为终态，再次结课被拒（INVALID_STATUS_TRANSITION）")
    void completeCompletedClassShouldFail() {
        Long classId = newClassWithStatus(TeachingClassStatus.COMPLETED.getCode());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> teachingClassService.complete(classId, 1L));
        assertEquals(ErrorCode.INVALID_STATUS_TRANSITION.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("停开：reason 为空被拒（BAD_REQUEST_PARAM）")
    void cancelActiveClassShouldRequireReason() {
        Long classId = newClassWithStatus(TeachingClassStatus.ACTIVE.getCode());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> teachingClassService.cancel(classId, null, 1L));
        assertEquals(ErrorCode.BAD_REQUEST_PARAM.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("停开：ACTIVE → CANCELLED 携带 reason 成功")
    void cancelActiveClassShouldSucceedWithReason() {
        Long classId = newClassWithStatus(TeachingClassStatus.ACTIVE.getCode());
        teachingClassService.cancel(classId, "选课人数不足", 1L);
        TeachingClassVO vo = teachingClassService.getById(classId);
        assertEquals(TeachingClassStatus.CANCELLED.getCode(), vo.getStatus());
    }

    @Test
    @DisplayName("创建教学班默认状态为 ACTIVE(1)，不是 CANCELLED(0)")
    void createTeachingClassShouldDefaultToActive() {
        Long teacherId = insertTeacher();
        Long catId = insertCategory();
        Long courseId = insertCourse(catId, teacherId);

        TeachingClassCreateRequest req = new TeachingClassCreateRequest();
        req.setCourseId(courseId);
        req.setTeacherId(teacherId);
        req.setName("默认状态教学班-" + uniq());
        req.setMaxStudents(60);
        req.setSemester("2026春");

        TeachingClassVO vo = teachingClassService.create(req);
        assertNotNull(vo.getId());
        assertEquals(TeachingClassStatus.ACTIVE.getCode(), vo.getStatus());
    }
}
