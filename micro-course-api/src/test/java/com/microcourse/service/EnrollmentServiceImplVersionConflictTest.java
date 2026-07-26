package com.microcourse.service;

import com.microcourse.dto.EnrollmentUpdateRequest;
import com.microcourse.entity.Course;
import com.microcourse.entity.Enrollment;
import com.microcourse.enums.EnrollmentStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentHistoryRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.OrderRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.impl.EnrollmentLifecycleServiceImpl;
import com.microcourse.metrics.EnrollmentMetrics;
import com.microcourse.repository.ClassesRepository;
import com.microcourse.repository.MajorRepository;
import com.microcourse.repository.CoursePrerequisiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * EnrollmentLifecycleServiceImpl 乐观锁版本冲突单元测试。
 *
 * <p>纯 Mockito，不依赖 Spring 容器。验证：
 * <ul>
 *   <li>updateEnrollment 中 updateById 返回 0 抛 CONCURRENT_MODIFICATION</li>
 *   <li>cancelEnrollment 中 updateById 返回 0 抛 CONCURRENT_MODIFICATION 且无副作用</li>
 * </ul>
 *
 * <p>2026-07-25: 原 EnrollmentServiceImpl 的状态机写操作已拆入
 * {@link EnrollmentLifecycleServiceImpl}，测试目标随之迁移。</p>
 */
@DisplayName("EnrollmentLifecycleServiceImpl 乐观锁冲突单元测试")
class EnrollmentServiceImplVersionConflictTest {

    @Mock private CoursePrerequisiteRepository coursePrerequisiteRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private EnrollmentHistoryRepository enrollmentHistoryRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private UserRepository userRepository;
    @Mock private ClassesRepository classesRepository;
    @Mock private MajorRepository majorRepository;
    @Mock private CertificateService certificateService;
    @Mock private BadgeService badgeService;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderService orderService;
    @Mock private CourseService courseService;
    @Mock private NotificationService notificationService;
    @Mock private EnrollmentMetrics metrics;

    private EnrollmentLifecycleServiceImpl lifecycleService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        lifecycleService = new EnrollmentLifecycleServiceImpl(
                coursePrerequisiteRepository, enrollmentRepository,
                enrollmentHistoryRepository, courseRepository, userRepository,
                classesRepository, majorRepository, certificateService,
                badgeService, orderRepository, orderService,
                courseService, notificationService, metrics);

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
    // 1. updateEnrollment — updateById 返回 0 → CONCURRENT_MODIFICATION
    // ====================================================================

    @Test
    @DisplayName("[P0] updateEnrollment: updateById 返回 0 抛 CONCURRENT_MODIFICATION")
    void updateEnrollment_updateByIdReturnsZero_throwsConcurrentModification() {
        // 准备 enrollment（version=1）
        Enrollment enrollment = new Enrollment();
        enrollment.setId(100L);
        enrollment.setCourseId(1L);
        enrollment.setUserId(2L);
        enrollment.setEnrollmentStatus(EnrollmentStatus.APPROVED.getValue());
        enrollment.setVersion(1);

        Course course = new Course();
        course.setId(1L);
        course.setTeacherId(1L);

        when(enrollmentRepository.selectById(100L)).thenReturn(enrollment);
        when(courseRepository.selectById(1L)).thenReturn(course);
        // 模拟 updateById 返回 0 → 版本冲突
        when(enrollmentRepository.updateById(any())).thenReturn(0);

        EnrollmentUpdateRequest req = new EnrollmentUpdateRequest();
        req.setFinalScore(BigDecimal.valueOf(90));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> lifecycleService.updateEnrollment(100L, req));
        assertEquals(ErrorCode.CONCURRENT_MODIFICATION.getCode(), ex.getCode(),
                "版本冲突应抛出 CONCURRENT_MODIFICATION");
        assertEquals(409, ex.getHttpStatus());
        // 验证 updateById 被调用且没有被 mock 的副作用干扰
        verify(enrollmentRepository).updateById(any());
    }

    // ====================================================================
    // 2. cancelEnrollment — updateById 返回 0 → CONCURRENT_MODIFICATION + 无副作用
    // ====================================================================

    @Test
    @DisplayName("[P0] cancelEnrollment: updateById 返回 0 抛 CONCURRENT_MODIFICATION 且无副作用")
    void cancelEnrollment_updateByIdReturnsZero_throwsAndNoSideEffects() {
        // 准备 APPROVED enrollment（version=1，wasEnrolled=true）
        Enrollment enrollment = new Enrollment();
        enrollment.setId(100L);
        enrollment.setCourseId(1L);
        enrollment.setUserId(2L);
        enrollment.setEnrollmentStatus(EnrollmentStatus.APPROVED.getValue());
        enrollment.setProgress(30.0); // < 50%，通过进度检查
        enrollment.setVersion(1);

        Course course = new Course();
        course.setId(1L);
        course.setTeacherId(1L);

        when(enrollmentRepository.selectOne(any())).thenReturn(enrollment);
        when(courseRepository.selectById(1L)).thenReturn(course);
        // updateById 返回 0 → 版本冲突
        when(enrollmentRepository.updateById(any())).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> lifecycleService.cancelEnrollment(100L, 1L));
        assertEquals(ErrorCode.CONCURRENT_MODIFICATION.getCode(), ex.getCode(),
                "版本冲突应抛出 CONCURRENT_MODIFICATION");
        assertEquals(409, ex.getHttpStatus());

        // ★ 关键断言：CAS 失败后不得有任何副作用
        // 1. 不写审计历史
        verify(enrollmentHistoryRepository, never()).insert(any());
        // 2. 不扣减 student_count
        verify(courseRepository, never()).atomicDecrementStudentCount(any());
        // 3. 不触发候补晋升
        // promoteFirstWaitlistToEnrolled 是私有方法，verify 通过 no side effects 间接证明
        // 4. 不触发退款
        verify(orderRepository, never()).selectOne(any());
        // 5. 不发送通知
        verify(notificationService, never()).notifyAsync(anyLong(), any(), any(), any(), anyLong());

        // 验证 updateById 确实被调用了
        verify(enrollmentRepository).updateById(any());
    }

    /**
     * WAITLIST 状态退课（wasEnrolled=false）不会触发任何副作用，即使 CAS 成功。
     * 本测试验证 CAS 失败时 WAITLIST 退课也不产生副作用。
     */
    @Test
    @DisplayName("[P0] cancelEnrollment: WAITLIST 下 CAS 失败也不产生副作用")
    void cancelEnrollment_WaitlistCASFailed_NoSideEffects() {
        // 准备 WAITLIST enrollment（wasEnrolled=false）
        Enrollment enrollment = new Enrollment();
        enrollment.setId(101L);
        enrollment.setCourseId(1L);
        enrollment.setUserId(2L);
        enrollment.setEnrollmentStatus(EnrollmentStatus.WAITLIST.getValue());
        enrollment.setVersion(1);

        when(enrollmentRepository.selectOne(any())).thenReturn(enrollment);
        when(enrollmentRepository.updateById(any())).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> lifecycleService.cancelEnrollment(101L, 1L));
        assertEquals(ErrorCode.CONCURRENT_MODIFICATION.getCode(), ex.getCode());

        // 无副作用
        verify(enrollmentHistoryRepository, never()).insert(any());
        verify(courseRepository, never()).atomicDecrementStudentCount(any());
        verify(orderRepository, never()).selectOne(any());
    }
}
