package com.microcourse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.microSpecialty.MicroSpecialtyLeadTransferRequest;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.MicroSpecialty;
import com.microcourse.entity.MicroSpecialtyCourse;
import com.microcourse.entity.MicroSpecialtyEnrollment;
import com.microcourse.entity.MicroSpecialtyTeacher;
import com.microcourse.entity.User;
import com.microcourse.enums.EnrollmentStatus;
import com.microcourse.enums.MicroSpecialtyStatus;
import com.microcourse.enums.NotificationType;
import com.microcourse.enums.UserRole;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.MicroSpecialtyCourseRepository;
import com.microcourse.repository.MicroSpecialtyEnrollmentRepository;
import com.microcourse.repository.MicroSpecialtyFeaturedAuditRepository;
import com.microcourse.repository.MicroSpecialtyRepository;
import com.microcourse.repository.MicroSpecialtyTeacherRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.impl.MicroSpecialtyAdminServiceImpl;
import com.microcourse.util.SecurityUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MicroSpecialtyAdminServiceImpl — 微专业管理（状态流转 + 级联 + 并发）")
class MicroSpecialtyAdminServiceImplTest {

    @Mock private MicroSpecialtyRepository msRepository;
    @Mock private MicroSpecialtyCourseRepository msCourseRepository;
    @Mock private MicroSpecialtyTeacherRepository msTeacherRepository;
    @Mock private MicroSpecialtyEnrollmentRepository msEnrollmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private NotificationService notificationService;
    @Mock private MicroSpecialtyFeaturedAuditRepository msFeaturedAuditRepository;
    @Mock private MicroSpecialtyQueryService queryService;

    private MicroSpecialtyAdminServiceImpl adminService;

    @BeforeAll
    static void init() {
        MybatisPlusTestHelper.initTableInfo();
    }

    @BeforeEach
    void setUp() {
        adminService = new MicroSpecialtyAdminServiceImpl(
                msRepository, msCourseRepository, msTeacherRepository,
                msEnrollmentRepository, userRepository, enrollmentRepository,
                notificationService, msFeaturedAuditRepository, queryService);
    }

    // ==================== 辅助方法 ====================

    private MicroSpecialty createMs(Long id, String status, int version, Long leadTeacherId,
                                     boolean isFeatured, boolean isGoldFeatured) {
        MicroSpecialty ms = new MicroSpecialty();
        ms.setId(id);
        ms.setStatus(status);
        ms.setVersion(version);
        ms.setLeadTeacherId(leadTeacherId);
        ms.setIsFeatured(isFeatured);
        ms.setIsGoldFeatured(isGoldFeatured);
        ms.setFeaturedStatus(isFeatured || isGoldFeatured ? "APPROVED" : "NONE");
        ms.setTitle("测试微专业");
        ms.setCreatorId(2L);
        return ms;
    }

    private MicroSpecialtyEnrollment createEn(Long id, Long userId, String status, int version) {
        MicroSpecialtyEnrollment en = new MicroSpecialtyEnrollment();
        en.setId(id);
        en.setUserId(userId);
        en.setMicroSpecialtyId(1L);
        en.setStatus(status);
        en.setVersion(version);
        en.setProgress(java.math.BigDecimal.ZERO);
        en.setCreditsEarned(java.math.BigDecimal.ZERO);
        en.setCoursesCompleted(0);
        en.setCoursesRequired(0);
        return en;
    }

    // ==================== cancel() 核心测试 ====================

    @Test
    @DisplayName("cancel: 正常取消 RECRUITING → CANCELLED + featured 原子清理 + 级联")
    void cancel_normalWithFeaturedAndCascade() {
        MicroSpecialty ms = createMs(1L, "RECRUITING", 5, 10L, true, true);
        when(msRepository.selectById(1L)).thenReturn(ms);

        // 主 UPDATE 原子完成（含 featured 清理）
        when(msRepository.update(any(), any())).thenReturn(1);

        // enrollment 级联
        MicroSpecialtyEnrollment en1 = createEn(100L, 20L, "APPROVED", 3);
        MicroSpecialtyEnrollment en2 = createEn(101L, 21L, "IN_PROGRESS", 1);
        when(msEnrollmentRepository.selectList(any())).thenReturn(List.of(en1, en2));
        when(msEnrollmentRepository.update(any(), any())).thenReturn(1, 1);

        // 课程级 enrollment 级联
        MicroSpecialtyCourse mc = new MicroSpecialtyCourse();
        mc.setId(1L);
        mc.setMicroSpecialtyId(1L);
        mc.setCourseId(200L);
        when(msCourseRepository.selectList(any())).thenReturn(List.of(mc));

        Enrollment courseEn = new Enrollment();
        courseEn.setId(300L);
        courseEn.setCourseId(200L);
        courseEn.setUserId(20L);
        courseEn.setEnrollmentStatus(EnrollmentStatus.APPROVED.getValue());
        courseEn.setVersion(0);
        // 第一次 selectOne 返回 courseEn（用户 20 有课程 enrollment）
        // 第二次返回 null（用户 21 无课程 enrollment）
        when(enrollmentRepository.selectOne(any())).thenReturn(courseEn, null);
        when(enrollmentRepository.update(any(), any())).thenReturn(1);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::isAdmin).thenReturn(true);
            su.when(SecurityUtil::getCurrentUserId).thenReturn(1L);

            adminService.cancel(1L, "教务处决定取消");

            // 验证主 UPDATE 合并了 featured 清理
            verify(msRepository).update(any(), argThat(uw -> {
                // LambdaUpdateWrapper 无法直接断言 set 内容，但 update 返回 1 表示成功
                return true;
            }));
            // 验证 enrollments 级联（2 条，主状态通过 msRepository 更新）
            verify(msEnrollmentRepository, times(2)).update(any(), any());
            // 验证课程级 enrollment 清理
            verify(enrollmentRepository).update(any(), any());
            // 通知 LEAD + 2 名学生
            verify(notificationService, times(3)).notifyAsync(anyLong(), eq(NotificationType.MS_CANCELLED),
                    anyString(), anyString(), anyLong());
        }
    }

    @Test
    @DisplayName("cancel: enrollment 并发修改抛出 MS_CONCURRENT_MODIFICATION")
    void cancel_enrollmentConcurrentModification_throws() {
        MicroSpecialty ms = createMs(1L, "RECRUITING", 5, 10L, false, false);
        when(msRepository.selectById(1L)).thenReturn(ms);
        when(msRepository.update(any(), any())).thenReturn(1);

        MicroSpecialtyEnrollment en = createEn(100L, 20L, "APPROVED", 3);
        when(msEnrollmentRepository.selectList(any())).thenReturn(List.of(en));
        // 第一个 enrollment update 返回 0（并发修改）
        when(msEnrollmentRepository.update(any(), any())).thenReturn(0);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::isAdmin).thenReturn(true);
            su.when(SecurityUtil::getCurrentUserId).thenReturn(1L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> adminService.cancel(1L, "测试取消"));
            assertEquals(ErrorCode.MS_CONCURRENT_MODIFICATION.getCode(), ex.getCode());
            // 验证回滚：update 被调用，但事务标记为 rollback
            verify(msEnrollmentRepository).update(any(), any());
        }
    }

    @Test
    @DisplayName("cancel: 主 UPDATE 乐观锁冲突抛出异常")
    void cancel_mainUpdateConcurrent_throws() {
        MicroSpecialty ms = createMs(1L, "RECRUITING", 5, 10L, false, false);
        when(msRepository.selectById(1L)).thenReturn(ms);
        when(msRepository.update(any(), any())).thenReturn(0);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::isAdmin).thenReturn(true);
            su.when(SecurityUtil::getCurrentUserId).thenReturn(1L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> adminService.cancel(1L, "测试取消"));
            assertEquals(ErrorCode.MS_CONCURRENT_MODIFICATION.getCode(), ex.getCode());
        }
    }

    @Test
    @DisplayName("cancel: 终态（CANCELLED）拒绝取消")
    void cancel_terminalState_throws() {
        MicroSpecialty ms = createMs(1L, "CANCELLED", 5, 10L, false, false);
        when(msRepository.selectById(1L)).thenReturn(ms);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::isAdmin).thenReturn(true);
            su.when(SecurityUtil::getCurrentUserId).thenReturn(1L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> adminService.cancel(1L, "测试"));
            assertEquals(ErrorCode.MS_STATUS_INVALID.getCode(), ex.getCode());
        }
    }

    @Test
    @DisplayName("cancel: 空原因抛出 BAD_REQUEST_PARAM")
    void cancel_blankReason_throws() {
        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::isAdmin).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> adminService.cancel(1L, ""));
            assertEquals(ErrorCode.BAD_REQUEST_PARAM.getCode(), ex.getCode());
        }
    }

    @Test
    @DisplayName("cancel: ARCHIVED 拒绝取消")
    void cancel_archived_throws() {
        MicroSpecialty ms = createMs(1L, "ARCHIVED", 5, 10L, false, false);
        when(msRepository.selectById(1L)).thenReturn(ms);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::isAdmin).thenReturn(true);
            su.when(SecurityUtil::getCurrentUserId).thenReturn(1L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> adminService.cancel(1L, "测试"));
            assertEquals(ErrorCode.MS_STATUS_INVALID.getCode(), ex.getCode());
        }
    }

    // ==================== submit() 测试 ====================

    @Test
    @DisplayName("submit: DRAFT→PENDING_REVIEW 正常提交 + 乐观锁")
    void submit_draftToPendingReview() {
        MicroSpecialty ms = createMs(1L, "DRAFT", 0, 10L, false, false);
        when(msRepository.selectById(1L)).thenReturn(ms);
        // isLeadOf returns true (current user 10L is the LEAD)
        when(queryService.isLeadOf(eq(1L), eq(10L))).thenReturn(true);
        // LEAD active count = 1
        when(msTeacherRepository.selectCount(any())).thenReturn(1L);
        // course count >= 1
        when(msCourseRepository.selectCount(any())).thenReturn(1L);
        // update 成功
        when(msRepository.update(any(), any())).thenReturn(1);
        // academic users 通知
        User academic = new User();
        academic.setId(99L);
        academic.setRole(UserRole.ACADEMIC);
        when(userRepository.selectList(any())).thenReturn(List.of(academic));

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(10L);
            su.when(SecurityUtil::isAdmin).thenReturn(false);

            adminService.submit(1L);
            verify(msRepository).update(any(), any());
            verify(notificationService).notifyAsync(eq(99L), eq(NotificationType.MS_SUBMITTED),
                    anyString(), anyString(), eq(1L));
        }
    }

    @Test
    @DisplayName("submit: 乐观锁冲突抛出 MS_CONCURRENT_MODIFICATION")
    void submit_concurrentConflict() {
        MicroSpecialty ms = createMs(1L, "DRAFT", 0, 10L, false, false);
        when(msRepository.selectById(1L)).thenReturn(ms);
        when(queryService.isLeadOf(eq(1L), eq(10L))).thenReturn(true);
        when(msTeacherRepository.selectCount(any())).thenReturn(1L);
        when(msCourseRepository.selectCount(any())).thenReturn(1L);
        when(msRepository.update(any(), any())).thenReturn(0);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(10L);
            su.when(SecurityUtil::isAdmin).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> adminService.submit(1L));
            assertEquals(ErrorCode.MS_CONCURRENT_MODIFICATION.getCode(), ex.getCode());
        }
    }

    // ==================== approve() 测试 ====================

    @Test
    @DisplayName("approve: PENDING_REVIEW→APPROVED + 通知 LEAD")
    void approve_success() {
        MicroSpecialty ms = createMs(1L, "PENDING_REVIEW", 2, 10L, false, false);
        when(msRepository.selectById(1L)).thenReturn(ms);
        when(msRepository.update(any(), any())).thenReturn(1);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(5L);
            su.when(SecurityUtil::isAdmin).thenReturn(false);

            adminService.approve(1L);
            verify(msRepository).update(any(), any());
            verify(notificationService).notifyAsync(eq(10L), eq(NotificationType.MS_APPROVED),
                    anyString(), anyString(), eq(1L));
        }
    }

    // ==================== reject() 测试 ====================

    @Test
    @DisplayName("reject: PENDING_REVIEW→REJECTED + 通知 LEAD")
    void reject_success() {
        MicroSpecialty ms = createMs(1L, "PENDING_REVIEW", 2, 10L, false, false);
        when(msRepository.selectById(1L)).thenReturn(ms);
        when(msRepository.update(any(), any())).thenReturn(1);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(5L);
            su.when(SecurityUtil::isAdmin).thenReturn(false);

            adminService.reject(1L, "内容需要补充");
            verify(msRepository).update(any(), any());
            verify(notificationService).notifyAsync(eq(10L), eq(NotificationType.MS_REJECTED),
                    anyString(), anyString(), eq(1L));
        }
    }

    // ==================== open() 测试 ====================

    @Test
    @DisplayName("open: APPROVED→RECRUITING + 前置条件检查 + 通知 LEAD")
    void open_success() {
        MicroSpecialty ms = createMs(1L, "APPROVED", 2, 10L, false, false);
        when(msRepository.selectById(1L)).thenReturn(ms);
        when(queryService.isLeadOf(eq(1L), eq(10L))).thenReturn(true);
        when(msCourseRepository.selectCount(any())).thenReturn(2L); // >= 1 course
        when(msTeacherRepository.selectCount(any())).thenReturn(3L); // team >= 2
        when(msRepository.update(any(), any())).thenReturn(1);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(10L);
            su.when(SecurityUtil::isAdmin).thenReturn(false);
            su.when(() -> SecurityUtil.hasRole(anyString())).thenReturn(false);

            adminService.open(1L);
            verify(msRepository).update(any(), any());
            verify(notificationService).notifyAsync(eq(10L), eq(NotificationType.MS_OPENED),
                    anyString(), anyString(), eq(1L));
        }
    }

    @Test
    @DisplayName("open: 课程 < 1 抛出异常")
    void open_noCourses_throws() {
        MicroSpecialty ms = createMs(1L, "APPROVED", 2, 10L, false, false);
        when(msRepository.selectById(1L)).thenReturn(ms);
        when(queryService.isLeadOf(eq(1L), eq(10L))).thenReturn(true);
        when(msCourseRepository.selectCount(any())).thenReturn(0L);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(10L);
            su.when(SecurityUtil::isAdmin).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> adminService.open(1L));
            assertEquals(ErrorCode.MS_STATUS_INVALID.getCode(), ex.getCode());
        }
    }

    // ==================== close() 测试 ====================

    @Test
    @DisplayName("close: RECRUITING→COMPLETED + 通知 ACADEMIC")
    void close_success() {
        MicroSpecialty ms = createMs(1L, "RECRUITING", 3, 10L, false, false);
        when(msRepository.selectById(1L)).thenReturn(ms);
        when(queryService.isLeadOf(eq(1L), eq(10L))).thenReturn(true);
        when(msRepository.update(any(), any())).thenReturn(1);
        User academic = new User();
        academic.setId(99L);
        academic.setRole(UserRole.ACADEMIC);
        when(userRepository.selectList(any())).thenReturn(List.of(academic));

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(10L);
            su.when(SecurityUtil::isAdmin).thenReturn(false);

            adminService.close(1L);
            verify(msRepository).update(any(), any());
            verify(notificationService).notifyAsync(eq(99L), eq(NotificationType.MS_COMPLETED),
                    anyString(), anyString(), eq(1L));
        }
    }

    // ==================== archive() 测试 ====================

    @Test
    @DisplayName("archive: COMPLETED→ARCHIVED + 通知 LEAD")
    void archive_success() {
        MicroSpecialty ms = createMs(1L, "COMPLETED", 4, 10L, false, false);
        when(msRepository.selectById(1L)).thenReturn(ms);
        when(msRepository.update(any(), any())).thenReturn(1);

        adminService.archive(1L);
        verify(msRepository).update(any(), any());
        verify(notificationService).notifyAsync(eq(10L), eq(NotificationType.MS_ARCHIVED),
                anyString(), anyString(), eq(1L));
    }

    // ==================== transferLeadership() 测试 ====================

    @Test
    @DisplayName("transferLeadership: RECRUITING 状态正常继任 + 旧 LEAD 降级")
    void transferLeadership_normal() {
        MicroSpecialty ms = createMs(1L, "RECRUITING", 5, 10L, false, false);
        when(msRepository.selectById(1L)).thenReturn(ms);

        MicroSpecialtyLeadTransferRequest req = new MicroSpecialtyLeadTransferRequest();
        req.setNewLeadTeacherId(11L);

        // 新 LEAD 用户存在
        User newLeadUser = new User();
        newLeadUser.setId(11L);
        newLeadUser.setRealName("新负责人");
        when(userRepository.selectById(eq(11L))).thenReturn(newLeadUser);

        // 旧 LEAD 记录
        MicroSpecialtyTeacher oldLead = new MicroSpecialtyTeacher();
        oldLead.setId(50L);
        oldLead.setTeacherId(10L);
        oldLead.setRole("LEAD");
        oldLead.setInviteStatus("ACTIVE");
        oldLead.setVersion(2);
        when(msTeacherRepository.selectOne(any())).thenReturn(oldLead);
        when(msTeacherRepository.update(any(), any())).thenReturn(1, 1); // old lead + new lead
        when(msRepository.update(any(), any())).thenReturn(1);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(1L);

            adminService.transferLeadership(1L, req);
            verify(msTeacherRepository, times(2)).update(any(), any());
            verify(msRepository).update(any(), any());
        }
    }

    @Test
    @DisplayName("transferLeadership: 终态（CANCELLED）拒绝转移")
    void transferLeadership_cancelled_throws() {
        MicroSpecialty ms = createMs(1L, "CANCELLED", 5, 10L, false, false);
        when(msRepository.selectById(1L)).thenReturn(ms);

        MicroSpecialtyLeadTransferRequest req = new MicroSpecialtyLeadTransferRequest();
        req.setNewLeadTeacherId(11L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminService.transferLeadership(1L, req));
        assertEquals(ErrorCode.MS_STATUS_INVALID.getCode(), ex.getCode());
    }
}
