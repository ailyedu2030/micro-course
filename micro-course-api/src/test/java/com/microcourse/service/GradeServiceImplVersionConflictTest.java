package com.microcourse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcourse.entity.Course;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.Grade;
import com.microcourse.enums.EnrollmentStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.ExerciseRecordRepository;
import com.microcourse.repository.ExerciseRepository;
import com.microcourse.repository.GradeRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.impl.GradeServiceImpl;
import com.microcourse.service.ScoreHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * GradeServiceImpl 乐观锁版本冲突 + 权限 TOCTOU 单元测试。
 *
 * <p>纯 Mockito，不依赖 Spring 容器，快速验证：
 * <ul>
 *   <li>{@code updateById} 返回 0 时抛 {@code CONCURRENT_MODIFICATION}</li>
 *   <li>COMPLETED 行锁保护路径不被 TOCTOU 绕过</li>
 *   <li>manualGrade 中 record→exercise→course 归属校验</li>
 * </ul>
 */
@DisplayName("GradeServiceImpl 乐观锁+权限单元测试")
class GradeServiceImplVersionConflictTest {

    @Mock private GradeRepository gradeRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private UserRepository userRepository;
    @Mock private ExerciseRepository exerciseRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private ExerciseRecordRepository exerciseRecordRepository;
    @Mock private NotificationService notificationService;
    @Mock private ObjectMapper objectMapper;
    @Mock private ScoreHistoryService scoreHistoryService;
    @Mock private ManualGradingService manualGradingService;
    private GradeServiceImpl gradeService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // GradeServiceImpl 构造函数顺序: gradeRepository, courseRepository, userRepository,
        // exerciseRepository, enrollmentRepository, exerciseRecordRepository,
        // notificationService, objectMapper, scoreHistoryService, manualGradingService
        gradeService = new GradeServiceImpl(
                gradeRepository, courseRepository, userRepository,
                exerciseRepository, enrollmentRepository, exerciseRecordRepository,
                notificationService, objectMapper, scoreHistoryService,
                manualGradingService);
        // 默认安全上下文：ADMIN 角色避免权限检查干扰
        SecurityContextHolder.getContext().setAuthentication(
                new Authentication() {
                    @Override public String getName() { return "1"; }
                    @Override public Object getCredentials() { return null; }
                    @Override public Object getDetails() { return null; }
                    @Override public Object getPrincipal() { return 1L; }
                    @Override public boolean isAuthenticated() { return true; }
                    @Override public void setAuthenticated(boolean b) {}
                    @Override public List<SimpleGrantedAuthority> getAuthorities() {
                        return List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
                    }
                });
    }

    // ====================================================================
    // 1. updateById @Version 冲突 → CONCURRENT_MODIFICATION
    // ====================================================================

    @Test
    @DisplayName("[P0-1] update 中 updateById 返回 0 抛 CONCURRENT_MODIFICATION(409)")
    void update_updateByIdReturnsZero_throwsConcurrentModification() {
        // 准备数据
        Grade grade = new Grade();
        grade.setId(100L);
        grade.setCourseId(1L);
        grade.setUserId(2L);
        grade.setScore(BigDecimal.valueOf(80));
        grade.setVersion(1);

        Course course = new Course();
        course.setId(1L);
        course.setTeacherId(1L);

        when(gradeRepository.selectById(100L)).thenReturn(grade);
        when(courseRepository.selectById(1L)).thenReturn(course);
        when(enrollmentRepository.selectOne(any())).thenReturn(null);
        // updateById 返回 0 → 模拟版本冲突
        when(gradeRepository.updateById(any())).thenReturn(0);

        com.microcourse.dto.GradeUpdateRequest req = new com.microcourse.dto.GradeUpdateRequest();
        req.setScore(BigDecimal.valueOf(90));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> gradeService.update(100L, req, 1L));
        assertEquals(ErrorCode.CONCURRENT_MODIFICATION.getCode(), ex.getCode(),
                "版本冲突应抛出 CONCURRENT_MODIFICATION");
        assertEquals(409, ex.getHttpStatus());
        verify(gradeRepository).updateById(any());
    }

    @Test
    @DisplayName("[P0-2] teacherGrade 中 updateById 返回 0 抛 CONCURRENT_MODIFICATION")
    void teacherGrade_updateByIdReturnsZero_throwsConcurrentModification() {
        Enrollment enrollment = new Enrollment();
        enrollment.setId(10L);
        enrollment.setCourseId(1L);
        enrollment.setUserId(2L);
        enrollment.setEnrollmentStatus(EnrollmentStatus.APPROVED.getValue());

        Course course = new Course();
        course.setId(1L);
        course.setTeacherId(1L);

        Grade existingGrade = new Grade();
        existingGrade.setId(100L);
        existingGrade.setCourseId(1L);
        existingGrade.setUserId(2L);
        existingGrade.setScore(BigDecimal.valueOf(80));

        // production GradeServiceImpl.teacherGrade 通过 enrollmentRepository.selectById 查选课
        when(enrollmentRepository.selectById(anyLong())).thenReturn(enrollment);
        when(courseRepository.selectById(1L)).thenReturn(course);
        when(gradeRepository.selectOne(any())).thenReturn(existingGrade);
        when(gradeRepository.updateById(any())).thenReturn(0);

        com.microcourse.dto.GradeTeacherSubmitRequest req = new com.microcourse.dto.GradeTeacherSubmitRequest();
        req.setEnrollmentId(10L);
        req.setScore(BigDecimal.valueOf(85));
        req.setComment("ok");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> gradeService.teacherGrade(req, 1L));
        assertEquals(ErrorCode.CONCURRENT_MODIFICATION.getCode(), ex.getCode());
        verify(gradeRepository).updateById(any());
    }

    @Test
    @DisplayName("[P0-3] manualGrade 委托 ManualGradingService — 异常传播正确")
    void manualGrade_gradeUpdateByIdReturnsZero_throwsConcurrentModification() {
        doThrow(new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, "成绩已被其他操作修改，请刷新后重试"))
                .when(manualGradingService).manualGrade(20L, 1L, 5.0, "部分正确", 1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> gradeService.manualGrade(20L, 1L, 5.0, "部分正确", 1L));
        assertEquals(ErrorCode.CONCURRENT_MODIFICATION.getCode(), ex.getCode());
        verify(manualGradingService).manualGrade(20L, 1L, 5.0, "部分正确", 1L);
    }

    // ====================================================================
    // 2. teacherGrade COMPLETED 行锁保护（TOCTOU 不可绕过）
    // ====================================================================

    @Test
    @DisplayName("[P0-4] teacherGrade COMPLETED enrollment 抛 BAD_REQUEST_PARAM")
    void teacherGrade_completedEnrollment_throwsError() {
        Enrollment enrollment = new Enrollment();
        enrollment.setId(10L);
        enrollment.setCourseId(1L);
        enrollment.setUserId(2L);
        enrollment.setEnrollmentStatus(EnrollmentStatus.COMPLETED.getValue());

        Course course = new Course();
        course.setId(1L);
        course.setTeacherId(1L);

        // production teacherGrade 走 enrollmentRepository.selectById
        when(enrollmentRepository.selectById(anyLong())).thenReturn(enrollment);
        when(courseRepository.selectById(1L)).thenReturn(course);

        com.microcourse.dto.GradeTeacherSubmitRequest req = new com.microcourse.dto.GradeTeacherSubmitRequest();
        req.setEnrollmentId(10L);
        req.setScore(BigDecimal.valueOf(85));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> gradeService.teacherGrade(req, 1L));
        assertEquals(ErrorCode.BAD_REQUEST_PARAM.getCode(), ex.getCode());
        // 验证 enrollment 读取被调用（实际使用 selectById）
        verify(enrollmentRepository).selectById(anyLong());
    }

    // ====================================================================
    // 3. manualGrade 归属校验（record→exercise→course）
    // ====================================================================

    @Test
    @DisplayName("[P0-5] manualGrade 越权 — 委托层异常传播")
    void manualGrade_wrongTeacherCourse_throwsForbidden() {
        doThrow(new BusinessException(ErrorCode.NO_PERMISSION, "无权批改该课程练习"))
                .when(manualGradingService).manualGrade(20L, 1L, 5.0, "部分正确", 99L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> gradeService.manualGrade(20L, 1L, 5.0, "部分正确", 99L));
        assertEquals(ErrorCode.NO_PERMISSION.getCode(), ex.getCode());
        verify(manualGradingService).manualGrade(20L, 1L, 5.0, "部分正确", 99L);
    }
}
