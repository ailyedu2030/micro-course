package com.microcourse.service;

import com.microcourse.dto.TeachingClassUpdateRequest;
import com.microcourse.entity.TeachingClass;
import com.microcourse.entity.TeachingClassStudent;
import com.microcourse.enums.TeachingClassStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.ClassScheduleRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.TeachingClassRepository;
import com.microcourse.repository.TeachingClassStudentRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.impl.TeachingClassServiceImpl;
import com.microcourse.util.SecurityUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;



/**
 * P0 修复测试：教学班权限与乐观锁。
 *
 * <p>【根因】TeachingClassServiceImpl.complete() 只检查状态白名单，未做对象级权限校验。
 * TEACHER 可越权结课他人教学班（operatorId 纯参数不参与鉴权）。
 * 此外 complete/cancel/update 三处 @Version 更新均未检查 updateById 影响行数，
 * 乐观锁失败时静默返回成功。
 *
 * <p>【修复】
 * - complete(): 增加 TEACHER 只能结课自己教学班；ADMIN/ACADEMIC 按契约允许
 * - complete/cancel/update: 检查 updateById 返回值，0 行则抛 CONCURRENT_MODIFICATION
 *
 * <p>【覆盖】
 * - TEACHER 结课自己的班成功
 * - TEACHER 结课他人班 NO_PERMISSION
 * - ADMIN 结课成功（不受 teacherId 约束）
 * - ACADEMIC 结课成功（不受 teacherId 约束）
 * - 乐观锁冲突时 CONCURRENT_MODIFICATION
 * - 状态机非法转换仍返回 INVALID_STATUS_TRANSITION（回归）
 *
 * <p>纯 Mockito 单元测试，无 Spring/DB 依赖。
 */
@DisplayName("P0: TeachingClassService 权限与乐观锁")
class TeachingClassServicePermissionTest {

    private TeachingClassRepository teachingClassRepository;
    private TeachingClassStudentRepository teachingClassStudentRepository;
    private ClassScheduleRepository classScheduleRepository;
    private CourseRepository courseRepository;
    private UserRepository userRepository;
    private TeachingClassServiceImpl teachingClassService;

    private static final Long TEACHER_A = 10L;
    private static final Long TEACHER_B = 20L;
    private static final Long ADMIN_USER = 1L;
    private static final Long ACADEMIC_USER = 2L;
    private static final Long CLASS_ID = 100L;

    @BeforeEach
    void setUp() {
        teachingClassRepository = Mockito.mock(TeachingClassRepository.class);
        teachingClassStudentRepository = Mockito.mock(TeachingClassStudentRepository.class);
        classScheduleRepository = Mockito.mock(ClassScheduleRepository.class);
        courseRepository = Mockito.mock(CourseRepository.class);
        userRepository = Mockito.mock(UserRepository.class);

        teachingClassService = new TeachingClassServiceImpl(
                teachingClassRepository,
                teachingClassStudentRepository,
                classScheduleRepository,
                courseRepository,
                userRepository);
    }

    /** 构造 ACTIVE 教学班，归属 TEACHER_A。 */
    private TeachingClass activeClassOwnedByTeacherA() {
        TeachingClass tc = new TeachingClass();
        tc.setId(CLASS_ID);
        tc.setTeacherId(TEACHER_A);
        tc.setStatus(TeachingClassStatus.ACTIVE.getCode());
        tc.setVersion(0);
        tc.setName("测试教学班");
        return tc;
    }

    // =============== complete() 权限测试 ===============

    @Nested
    @DisplayName("complete() 权限校验")
    class CompletePermissionTest {

        @Test
        @DisplayName("TEACHER 结课自己的教学班成功")
        void teacherCompleteOwnClassShouldSucceed() {
            TeachingClass tc = activeClassOwnedByTeacherA();
            when(teachingClassRepository.selectById(CLASS_ID)).thenReturn(tc);
            when(teachingClassRepository.updateById(any())).thenReturn(1);

            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(TEACHER_A);
                su.when(() -> SecurityUtil.hasRole("ACADEMIC")).thenReturn(false);
                su.when(SecurityUtil::isAdmin).thenReturn(false);

                teachingClassService.complete(CLASS_ID, TEACHER_A);

                ArgumentCaptor<TeachingClass> captor = ArgumentCaptor.forClass(TeachingClass.class);
                verify(teachingClassRepository).updateById(captor.capture());
                assertEquals(TeachingClassStatus.COMPLETED.getCode(), captor.getValue().getStatus(),
                        "状态应从 ACTIVE 变为 COMPLETED");
            }
        }

        @Test
        @DisplayName("TEACHER 结课他人教学班抛 NO_PERMISSION")
        void teacherCompleteOthersClassShouldThrowNoPermission() {
            TeachingClass tc = activeClassOwnedByTeacherA();
            when(teachingClassRepository.selectById(CLASS_ID)).thenReturn(tc);

            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(TEACHER_B);
                su.when(() -> SecurityUtil.hasRole("ACADEMIC")).thenReturn(false);
                su.when(SecurityUtil::isAdmin).thenReturn(false);

                BusinessException ex = assertThrows(BusinessException.class,
                        () -> teachingClassService.complete(CLASS_ID, TEACHER_B));
                assertEquals(ErrorCode.NO_PERMISSION.getCode(), ex.getCode(),
                        "越权教师应收到 NO_PERMISSION");
            }
        }

        @Test
        @DisplayName("ADMIN 结课任何教学班成功（不受 teacherId 约束）")
        void adminCompleteAnyClassShouldSucceed() {
            TeachingClass tc = activeClassOwnedByTeacherA();
            when(teachingClassRepository.selectById(CLASS_ID)).thenReturn(tc);
            when(teachingClassRepository.updateById(any())).thenReturn(1);

            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(ADMIN_USER);
                su.when(SecurityUtil::isAdmin).thenReturn(true);

                teachingClassService.complete(CLASS_ID, ADMIN_USER);

                ArgumentCaptor<TeachingClass> captor = ArgumentCaptor.forClass(TeachingClass.class);
                verify(teachingClassRepository).updateById(captor.capture());
                assertEquals(TeachingClassStatus.COMPLETED.getCode(), captor.getValue().getStatus());
            }
        }

        @Test
        @DisplayName("ACADEMIC 结课任何教学班成功（不受 teacherId 约束）")
        void academicCompleteAnyClassShouldSucceed() {
            TeachingClass tc = activeClassOwnedByTeacherA();
            when(teachingClassRepository.selectById(CLASS_ID)).thenReturn(tc);
            when(teachingClassRepository.updateById(any())).thenReturn(1);

            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(ACADEMIC_USER);
                su.when(() -> SecurityUtil.hasRole("ACADEMIC")).thenReturn(true);
                su.when(SecurityUtil::isAdmin).thenReturn(false);

                teachingClassService.complete(CLASS_ID, ACADEMIC_USER);

                ArgumentCaptor<TeachingClass> captor = ArgumentCaptor.forClass(TeachingClass.class);
                verify(teachingClassRepository).updateById(captor.capture());
                assertEquals(TeachingClassStatus.COMPLETED.getCode(), captor.getValue().getStatus());
            }
        }
    }

    // =============== 乐观锁测试（complete / cancel / update） ===============

    @Nested
    @DisplayName("complete/cancel/update 乐观锁")
    class OptimisticLockTest {

        @Test
        @DisplayName("complete 乐观锁冲突抛 CONCURRENT_MODIFICATION")
        void completeOptimisticLockConflictShouldThrow() {
            TeachingClass tc = activeClassOwnedByTeacherA();
            when(teachingClassRepository.selectById(CLASS_ID)).thenReturn(tc);
            // 模拟 updateById 返回 0（版本冲突或记录被删）
            when(teachingClassRepository.updateById(any())).thenReturn(0);

            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(TEACHER_A);
                su.when(() -> SecurityUtil.hasRole("ACADEMIC")).thenReturn(false);
                su.when(SecurityUtil::isAdmin).thenReturn(false);

                BusinessException ex = assertThrows(BusinessException.class,
                        () -> teachingClassService.complete(CLASS_ID, TEACHER_A));
                assertEquals(ErrorCode.CONCURRENT_MODIFICATION.getCode(), ex.getCode(),
                        "乐观锁失败应抛 CONCURRENT_MODIFICATION");
            }
        }

        @Test
        @DisplayName("cancel 乐观锁冲突抛 CONCURRENT_MODIFICATION")
        void cancelOptimisticLockConflictShouldThrow() {
            TeachingClass tc = activeClassOwnedByTeacherA();
            when(teachingClassRepository.selectById(CLASS_ID)).thenReturn(tc);
            when(teachingClassRepository.updateById(any())).thenReturn(0);

            // cancel 不依赖 SecurityUtil（Controller 层已限制 ADMIN/ACADEMIC），无需 mock
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> teachingClassService.cancel(CLASS_ID, "选课人数不足", ADMIN_USER));
            assertEquals(ErrorCode.CONCURRENT_MODIFICATION.getCode(), ex.getCode(),
                    "cancel 乐观锁失败应抛 CONCURRENT_MODIFICATION");
        }
    }

    // =============== 状态机回归测试 ===============

    @Nested
    @DisplayName("状态机非法转换回归")
    class StateMachineRegressionTest {

        @Test
        @DisplayName("COMPLETED 教学班再次结课抛 INVALID_STATUS_TRANSITION")
        void completeCompletedClassShouldThrowInvalidTransition() {
            TeachingClass tc = activeClassOwnedByTeacherA();
            tc.setStatus(TeachingClassStatus.COMPLETED.getCode());  // 已是终态
            when(teachingClassRepository.selectById(CLASS_ID)).thenReturn(tc);

            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(TEACHER_A);
                su.when(() -> SecurityUtil.hasRole("ACADEMIC")).thenReturn(false);
                su.when(SecurityUtil::isAdmin).thenReturn(false);

                BusinessException ex = assertThrows(BusinessException.class,
                        () -> teachingClassService.complete(CLASS_ID, TEACHER_A));
                assertEquals(ErrorCode.INVALID_STATUS_TRANSITION.getCode(), ex.getCode(),
                        "终态再次结课应抛 INVALID_STATUS_TRANSITION（非 NO_PERMISSION）");
            }
        }

        @Test
        @DisplayName("CANCELLED 教学班再次结课抛 INVALID_STATUS_TRANSITION")
        void completeCancelledClassShouldThrowInvalidTransition() {
            TeachingClass tc = activeClassOwnedByTeacherA();
            tc.setStatus(TeachingClassStatus.CANCELLED.getCode());  // 已是终态
            when(teachingClassRepository.selectById(CLASS_ID)).thenReturn(tc);

            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(TEACHER_A);
                su.when(() -> SecurityUtil.hasRole("ACADEMIC")).thenReturn(false);
                su.when(SecurityUtil::isAdmin).thenReturn(false);

                BusinessException ex = assertThrows(BusinessException.class,
                        () -> teachingClassService.complete(CLASS_ID, TEACHER_A));
                assertEquals(ErrorCode.INVALID_STATUS_TRANSITION.getCode(), ex.getCode(),
                        "CANCELLED 终态再次结课应抛 INVALID_STATUS_TRANSITION");
            }
        }
    }

    // =============== update() PUT status bypass 测试 ===============

    @Nested
    @DisplayName("PUT status bypass 防御")
    class UpdateStatusBypassTest {

        @Test
        @DisplayName("update 携带 status 字段抛 BAD_REQUEST_PARAM，阻止绕过状态机")
        void updateWithStatusShouldReject() {
            TeachingClass tc = activeClassOwnedByTeacherA();
            when(teachingClassRepository.selectById(CLASS_ID)).thenReturn(tc);

            TeachingClassUpdateRequest req = new TeachingClassUpdateRequest();
            req.setStatus(TeachingClassStatus.COMPLETED.getCode()); // 试图通过 PUT 结课

            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::isAdmin).thenReturn(true);

                BusinessException ex = assertThrows(BusinessException.class,
                        () -> teachingClassService.update(CLASS_ID, req));
                assertEquals(ErrorCode.BAD_REQUEST_PARAM.getCode(), ex.getCode(),
                        "PUT 携带 status 应抛 BAD_REQUEST_PARAM");
            }
        }

        @Test
        @DisplayName("update 不携带 status 正常更新其他字段")
        void updateWithoutStatusShouldSucceed() {
            TeachingClass tc = activeClassOwnedByTeacherA();
            when(teachingClassRepository.selectById(CLASS_ID)).thenReturn(tc);
            when(teachingClassRepository.updateById(any())).thenReturn(1);

            TeachingClassUpdateRequest req = new TeachingClassUpdateRequest();
            req.setName("更新后的教学班名");

            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::isAdmin).thenReturn(true);

                teachingClassService.update(CLASS_ID, req);

                ArgumentCaptor<TeachingClass> captor = ArgumentCaptor.forClass(TeachingClass.class);
                verify(teachingClassRepository).updateById(captor.capture());
                assertEquals("更新后的教学班名", captor.getValue().getName());
            }
        }
    }

    // =============== updateStudentStatus 白名单测试（V316 修复） ===============

    @Nested
    @DisplayName("updateStudentStatus 学生状态白名单（V316）")
    class UpdateStudentStatusWhitelistTest {

        private TeachingClass tc;
        private TeachingClassStudent record;

        @BeforeEach
        void setUp() {
            tc = activeClassOwnedByTeacherA();
            record = new TeachingClassStudent();
            record.setId(1L);
            record.setClassId(CLASS_ID);
            record.setUserId(999L);
            record.setStatus("ENROLLED");

            when(teachingClassRepository.selectById(CLASS_ID)).thenReturn(tc);
            when(teachingClassStudentRepository.selectList(any())).thenReturn(java.util.List.of(record));
            when(teachingClassStudentRepository.updateById(any())).thenReturn(1);
        }

        @Test
        @DisplayName("ENROLLED 合法")
        void enrolledStatusShouldBeAccepted() {
            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(ADMIN_USER);
                su.when(SecurityUtil::isAdmin).thenReturn(true);

                teachingClassService.updateStudentStatus(CLASS_ID, 999L, "ENROLLED");
                ArgumentCaptor<TeachingClassStudent> captor = ArgumentCaptor.forClass(TeachingClassStudent.class);
                verify(teachingClassStudentRepository).updateById(captor.capture());
                assertEquals("ENROLLED", captor.getValue().getStatus());
            }
        }

        @Test
        @DisplayName("APPROVED → COMPLETED 允许（已完成）")
        void approvedToCompletedShouldBeAccepted() {
            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(ADMIN_USER);
                su.when(SecurityUtil::isAdmin).thenReturn(true);

                teachingClassService.updateStudentStatus(CLASS_ID, 999L, "COMPLETED");
                ArgumentCaptor<TeachingClassStudent> captor = ArgumentCaptor.forClass(TeachingClassStudent.class);
                verify(teachingClassStudentRepository).updateById(captor.capture());
                assertEquals("COMPLETED", captor.getValue().getStatus());
            }
        }

        @Test
        @DisplayName("DROPPED 合法（V316 白名单允许）")
        void droppedStatusShouldBeAccepted() {
            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(TEACHER_A);

                teachingClassService.updateStudentStatus(CLASS_ID, 999L, "DROPPED");
                ArgumentCaptor<TeachingClassStudent> captor = ArgumentCaptor.forClass(TeachingClassStudent.class);
                verify(teachingClassStudentRepository).updateById(captor.capture());
                assertEquals("DROPPED", captor.getValue().getStatus());
            }
        }

        @Test
        @DisplayName("ACTIVE 非法（V316 已移除）")
        void activeStatusShouldBeRejected() {
            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(TEACHER_A);

                BusinessException ex = assertThrows(BusinessException.class,
                        () -> teachingClassService.updateStudentStatus(CLASS_ID, 999L, "ACTIVE"));
                assertEquals(ErrorCode.BAD_REQUEST_PARAM.getCode(), ex.getCode());
            }
        }

        @Test
        @DisplayName("SUSPENDED 非法（V316 已移除）")
        void suspendedStatusShouldBeRejected() {
            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(TEACHER_A);

                BusinessException ex = assertThrows(BusinessException.class,
                        () -> teachingClassService.updateStudentStatus(CLASS_ID, 999L, "SUSPENDED"));
                assertEquals(ErrorCode.BAD_REQUEST_PARAM.getCode(), ex.getCode());
            }
        }

        @Test
        @DisplayName("DISABLED 非法（V316 已移除）")
        void disabledStatusShouldBeRejected() {
            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(TEACHER_A);

                BusinessException ex = assertThrows(BusinessException.class,
                        () -> teachingClassService.updateStudentStatus(CLASS_ID, 999L, "DISABLED"));
                assertEquals(ErrorCode.BAD_REQUEST_PARAM.getCode(), ex.getCode());
            }
        }

        @Test
        @DisplayName("未知状态非法")
        void unknownStatusShouldBeRejected() {
            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(TEACHER_A);

                BusinessException ex = assertThrows(BusinessException.class,
                        () -> teachingClassService.updateStudentStatus(CLASS_ID, 999L, "INVALID_STATUS"));
                assertEquals(ErrorCode.BAD_REQUEST_PARAM.getCode(), ex.getCode());
            }
        }

        @Test
        @DisplayName("null 状态非法")
        void nullStatusShouldBeRejected() {
            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(TEACHER_A);

                BusinessException ex = assertThrows(BusinessException.class,
                        () -> teachingClassService.updateStudentStatus(CLASS_ID, 999L, null));
                assertEquals(ErrorCode.BAD_REQUEST_PARAM.getCode(), ex.getCode());
            }
        }
    }

    // =============== delete() 防御性角色校验测试 ===============

    @Nested
    @DisplayName("delete() 防御性角色校验")
    class DeleteDefensiveRoleCheckTest {

        @Test
        @DisplayName("ADMIN 删除成功")
        void adminDeleteShouldSucceed() {
            TeachingClass tc = activeClassOwnedByTeacherA();
            when(teachingClassRepository.selectById(CLASS_ID)).thenReturn(tc);
            when(teachingClassStudentRepository.selectCount(any())).thenReturn(0L);

            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::isAdmin).thenReturn(true);

                teachingClassService.delete(CLASS_ID);
                verify(teachingClassRepository).deleteById(CLASS_ID);
            }
        }

        @Test
        @DisplayName("TEACHER 删除被拒 NO_PERMISSION")
        void teacherDeleteShouldThrowNoPermission() {
            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::isAdmin).thenReturn(false);

                BusinessException ex = assertThrows(BusinessException.class,
                        () -> teachingClassService.delete(CLASS_ID));
                assertEquals(ErrorCode.NO_PERMISSION.getCode(), ex.getCode());
            }
        }

        @Test
        @DisplayName("ACADEMIC 删除被拒 NO_PERMISSION")
        void academicDeleteShouldThrowNoPermission() {
            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::isAdmin).thenReturn(false);

                BusinessException ex = assertThrows(BusinessException.class,
                        () -> teachingClassService.delete(CLASS_ID));
                assertEquals(ErrorCode.NO_PERMISSION.getCode(), ex.getCode());
            }
        }
    }
}
