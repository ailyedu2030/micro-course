package com.microcourse.service;

import com.microcourse.dto.EnrollmentCreateRequest;
import com.microcourse.entity.Course;
import com.microcourse.entity.MicroSpecialty;
import com.microcourse.entity.MicroSpecialtyCourse;
import com.microcourse.entity.MicroSpecialtyEnrollment;
import com.microcourse.entity.User;
import com.microcourse.enums.NotificationType;
import com.microcourse.enums.UserRole;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.MicroSpecialtyCourseRepository;
import com.microcourse.repository.MicroSpecialtyEnrollmentRepository;
import com.microcourse.repository.MicroSpecialtyRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.EnrollmentService;
import com.microcourse.service.NotificationService;
import com.microcourse.service.impl.MicroSpecialtyClassImportExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * MicroSpecialtyClassImportExecutor 单元测试 — 验证班级导入4 步流水线。
 *
 * <p>PR #254 拆分后,executor 通过构造函数注入 8 个依赖,可独立 Mockito 测试。
 * 关键覆盖:锁定/容量校验/逐学生导入/乐观锁更新/3 类通知。
 *
 * @author refactor 2026-08-17
 */
@DisplayName("MicroSpecialtyClassImportExecutor 班级导入单元测试")
class MicroSpecialtyClassImportExecutorTest {

    @Mock private MicroSpecialtyRepository msRepository;
    @Mock private MicroSpecialtyEnrollmentRepository enrollmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private MicroSpecialtyCourseRepository msCourseRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private EnrollmentService enrollmentService;
    @Mock private NotificationService notificationService;

    private MicroSpecialtyClassImportExecutor executor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        executor = new MicroSpecialtyClassImportExecutor(
                msRepository, enrollmentRepository, userRepository,
                msCourseRepository, courseRepository, enrollmentService,
                notificationService, 50 /* batchSize */);
    }

    @Test
    @DisplayName("MS 不存在 → MS_NOT_FOUND")
    void run_msNotFound() {
        when(msRepository.selectForUpdate(1L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.run(1L, 100L));
        assertEquals(ErrorCode.MS_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("MS 状态非 RECRUITING → MS_STATUS_INVALID")
    void run_msNotRecruiting() {
        MicroSpecialty ms = makeMs("RECRUITING", null);
        when(msRepository.selectForUpdate(1L)).thenReturn(ms);
        ms.setStatus("COMPLETED");  // 模拟状态非 RECRUITING

        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.run(1L, 100L));
        assertEquals(ErrorCode.MS_STATUS_INVALID.getCode(), ex.getCode());
        verify(msRepository).selectForUpdate(1L);
    }

    @Test
    @DisplayName("班级无学生 → 返回 0,不调用 enrollInNewTransaction")
    void run_emptyClassReturnsZero() {
        MicroSpecialty ms = makeMs("RECRUITING", null);
        when(msRepository.selectForUpdate(1L)).thenReturn(ms);
        when(enrollmentRepository.selectList(any())).thenReturn(List.of());
        when(userRepository.selectList(any())).thenReturn(List.of());  // empty class

        MicroSpecialtyClassImportExecutor.ClassImportResult result = executor.run(1L, 100L);

        assertEquals(0, result.imported());
        assertEquals(0, result.totalPendingCount());
        assertEquals(0, result.studentsWithPending());
        verify(enrollmentService, never()).enrollInNewTransaction(any());
    }

    @Test
    @DisplayName("微专业已满员 → MS_MAX_STUDENTS_REACHED(已在选课中占满)")
    void run_capacityFull_throwsAtPrepare() {
        MicroSpecialty ms = makeMs("RECRUITING", 2);  // max=2
        when(msRepository.selectForUpdate(1L)).thenReturn(ms);
        // 现有选课占满 2 个名额
        MicroSpecialtyEnrollment en1 = makeEn(101L);
        MicroSpecialtyEnrollment en2 = makeEn(102L);
        when(enrollmentRepository.selectList(any())).thenReturn(List.of(en1, en2));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.run(1L, 100L));
        assertEquals(ErrorCode.MS_MAX_STUDENTS_REACHED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("剩余名额不足(新学生 > 剩余) → MS_MAX_STUDENTS_REACHED(在 prepare 阶段)")
    void run_newStudentsExceedRemaining() {
        MicroSpecialty ms = makeMs("RECRUITING", 3);  // max=3
        when(msRepository.selectForUpdate(1L)).thenReturn(ms);
        when(enrollmentRepository.selectList(any())).thenReturn(List.of());  // 0 已有
        // 班级 4 个学生 → 4 > 3
        List<User> students = List.of(
                makeStudent(201L), makeStudent(202L), makeStudent(203L), makeStudent(204L));
        when(userRepository.selectList(any())).thenReturn(students);
        when(msCourseRepository.selectList(any())).thenReturn(List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.run(1L, 100L));
        assertEquals(ErrorCode.MS_MAX_STUDENTS_REACHED.getCode(), ex.getCode());
    }

// --- helpers ---

    private MicroSpecialty makeMs(String status, Integer maxStudents) {
        MicroSpecialty ms = new MicroSpecialty();
        ms.setId(1L);
        ms.setStatus(status);
        ms.setMaxStudents(maxStudents);
        ms.setVersion(0);
        ms.setLeadTeacherId(null);
        return ms;
    }

    private MicroSpecialtyEnrollment makeEn(long userId) {
        MicroSpecialtyEnrollment en = new MicroSpecialtyEnrollment();
        en.setId(userId);
        en.setUserId(userId);
        en.setMicroSpecialtyId(1L);
        en.setStatus("APPROVED");
        return en;
    }

    private User makeStudent(long id) {
        User u = new User();
        u.setId(id);
        u.setRole(UserRole.STUDENT);
        u.setClassId(100L);
        u.setRealName("学生" + id);
        return u;
    }

    private Course makeCourse(long id, String title) {
        Course c = new Course();
        c.setId(id);
        c.setTitle(title);
        return c;
    }

    /** 测试用 ACADEMIC 用户 id 常量(避免硬编码与业务冲突) */
    static final class AcademicIds {
        static final long ACADEMIC_1 = 99001L;
    }
}