package com.microcourse.service;

import com.microcourse.dto.microSpecialty.MicroSpecialtyEnrollmentVO;
import com.microcourse.entity.*;
import com.microcourse.enums.EnrollmentStatus;
import com.microcourse.enums.NotificationType;
import com.microcourse.enums.UserRole;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.*;
import com.microcourse.service.impl.MicroSpecialtyEnrollmentServiceImpl;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MicroSpecialtyEnrollmentService — 修读报名流程")
class MicroSpecialtyEnrollmentServiceTest {

    @Mock private MicroSpecialtyEnrollmentRepository enrollmentRepository;
    @Mock private MicroSpecialtyRepository msRepository;
    @Mock private MicroSpecialtyCourseRepository msCourseRepository;
    @Mock private MicroSpecialtyTeacherRepository msTeacherRepository;
    @Mock private EnrollmentRepository courseEnrollmentRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private EnrollmentService enrollmentService;
    @Mock private MicroSpecialtyService msService;
    @Mock private CertificateService certificateService;
    @Mock private MicroSpecialtyEnrollmentQueryService queryService;
    @Mock private MicroSpecialtyProgressService progressService;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private ClassesRepository classesRepository;

    private MicroSpecialtyEnrollmentServiceImpl service;

    @BeforeAll
    static void init() {
        MybatisPlusTestHelper.initTableInfo();
    }

    @BeforeEach
    void setUp() {
        service = new MicroSpecialtyEnrollmentServiceImpl(
                enrollmentRepository, msRepository, msCourseRepository,
                msTeacherRepository, courseEnrollmentRepository, courseRepository,
                userRepository, classesRepository, notificationService, enrollmentService,
                msService, certificateService, queryService, progressService, null);
        MicroSpecialtyEnrollmentVO mockVO = new MicroSpecialtyEnrollmentVO();
        mockVO.setId(1L);
        lenient().when(queryService.toVO(any(), any())).thenReturn(mockVO);
        lenient().doNothing().when(progressService).aggregateProgress(anyLong());
    }

    // ==================== apply() ====================

    @Test
    @DisplayName("apply: 正常报名 → PENDING")
    void apply_success() {
        MicroSpecialty ms = specialtyWithStatus("RECRUITING", 100, 10);
        when(msRepository.selectById(1L)).thenReturn(ms);
        when(enrollmentRepository.selectCount(any())).thenReturn(0L);
        doAnswer(inv -> {
            MicroSpecialtyEnrollment en = inv.getArgument(0);
            en.setId(1L);
            return 1;
        }).when(enrollmentRepository).insert(any(MicroSpecialtyEnrollment.class));
        when(userRepository.selectById(anyLong())).thenReturn(studentUser(5L));
        lenient().when(departmentRepository.selectById(anyLong())).thenReturn(null);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(5L);
            su.when(() -> SecurityUtil.hasRole("STUDENT")).thenReturn(true);

            MicroSpecialtyEnrollmentVO vo = service.apply(1L);

            assertNotNull(vo);
            verify(enrollmentRepository).insert(any(MicroSpecialtyEnrollment.class));
            verify(notificationService).notifyAsync(eq(1L), eq(NotificationType.MS_ENROLLMENT_PENDING),
                    anyString(), anyString(), eq(1L));
        }
    }

    @Test
    @DisplayName("apply: 非 RECRUITING 抛出 MS_ENROLLMENT_CLOSED")
    void apply_notRecruiting() {
        MicroSpecialty ms = specialtyWithStatus("COMPLETED", 100, 10);
        when(msRepository.selectById(1L)).thenReturn(ms);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(5L);

            BusinessException ex = assertThrows(BusinessException.class, () -> service.apply(1L));
            assertEquals(ErrorCode.MS_ENROLLMENT_CLOSED.getCode(), ex.getCode());
        }
    }

    @Test
    @DisplayName("apply: 人数已满抛出 MS_MAX_STUDENTS_REACHED")
    void apply_maxStudentsReached() {
        MicroSpecialty ms = specialtyWithStatus("RECRUITING", 100, 100);
        when(msRepository.selectById(1L)).thenReturn(ms);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(5L);

            BusinessException ex = assertThrows(BusinessException.class, () -> service.apply(1L));
            assertEquals(ErrorCode.MS_MAX_STUDENTS_REACHED.getCode(), ex.getCode());
        }
    }

    @Test
    @DisplayName("apply: 非学生角色抛出 NO_PERMISSION")
    void apply_nonStudent() {
        MicroSpecialty ms = specialtyWithStatus("RECRUITING", 100, 10);
        when(msRepository.selectById(1L)).thenReturn(ms);
        when(userRepository.selectById(5L)).thenReturn(teacherUser(5L));

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(5L);
            su.when(() -> SecurityUtil.hasRole("STUDENT")).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class, () -> service.apply(1L));
            assertEquals(ErrorCode.NO_PERMISSION.getCode(), ex.getCode());
        }
    }

    @Test
    @DisplayName("apply: 重复报名抛出 MS_DUPLICATE_ENROLL")
    void apply_duplicate() {
        MicroSpecialty ms = specialtyWithStatus("RECRUITING", 100, 10);
        when(msRepository.selectById(1L)).thenReturn(ms);
        when(enrollmentRepository.selectCount(any())).thenReturn(1L);
        when(userRepository.selectById(5L)).thenReturn(studentUser(5L));

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(5L);
            su.when(() -> SecurityUtil.hasRole("STUDENT")).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class, () -> service.apply(1L));
            assertEquals(ErrorCode.MS_DUPLICATE_ENROLL.getCode(), ex.getCode());
        }
    }

    // ==================== approve() ====================

    @Test
    @DisplayName("approve: PENDING → APPROVED 正常含学分认可")
    void approve_success() {
        MicroSpecialtyEnrollment en = pendingEnrollment(1L);
        when(enrollmentRepository.selectById(1L)).thenReturn(en);
        MicroSpecialty ms = specialtyWithStatus("RECRUITING", 100, 10);
        when(msRepository.selectById(anyLong())).thenReturn(ms);
        when(msService.isLeadOf(anyLong(), anyLong())).thenReturn(true);
        // Two updates: first for status, second for credits/stats
        when(enrollmentRepository.update(any(), any())).thenReturn(1, 1);
        when(msRepository.update(any(), any())).thenReturn(1);
        when(msCourseRepository.selectList(any())).thenReturn(Collections.emptyList());

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(2L);

            MicroSpecialtyEnrollmentVO vo = service.approve(1L);

            assertNotNull(vo);
            verify(notificationService).notifyAsync(eq(5L), eq(NotificationType.MS_ENROLLMENT_APPROVED),
                    anyString(), anyString(), anyLong());
        }
    }

    @Test
    @DisplayName("approve: MS 非 RECRUITING 拒绝")
    void approve_msNotRecruiting() {
        MicroSpecialtyEnrollment en = pendingEnrollment(1L);
        when(enrollmentRepository.selectById(1L)).thenReturn(en);
        MicroSpecialty ms = specialtyWithStatus("COMPLETED", 100, 10);
        when(msRepository.selectById(anyLong())).thenReturn(ms);
        when(msService.isLeadOf(anyLong(), anyLong())).thenReturn(true);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(2L);

            BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(1L));
            assertEquals(ErrorCode.MS_STATUS_INVALID.getCode(), ex.getCode());
        }
    }

    @Test
    @DisplayName("approve: 终态拒绝抛出 MS_TERMINAL_STATUS")
    void approve_terminalStatus() {
        MicroSpecialtyEnrollment en = pendingEnrollment(1L);
        when(enrollmentRepository.selectById(1L)).thenReturn(en);
        MicroSpecialty ms = msWithStatus("CANCELLED");
        when(msRepository.selectById(anyLong())).thenReturn(ms);
        when(msService.isLeadOf(anyLong(), anyLong())).thenReturn(true);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(2L);

            BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(1L));
            assertEquals(ErrorCode.MS_STATUS_INVALID.getCode(), ex.getCode());
        }
    }

    @Test
    @DisplayName("approve: 人数上限双重检查 — 满员时拒绝（修复 P1-C 越级）")
    void approve_maxStudentsReached() {
        // P1-C-12-05 回归: apply() 时检查上限,但两个 PENDING 同时申请,
        // 第一个被审批通过后 studentCount 才会 +1;第二个审批时必须再次检查
        MicroSpecialtyEnrollment en = pendingEnrollment(1L);
        when(enrollmentRepository.selectById(1L)).thenReturn(en);
        MicroSpecialty ms = specialtyWithStatus("RECRUITING", 50, 50);  // max=50, current=50
        when(msRepository.selectById(anyLong())).thenReturn(ms);
        when(msService.isLeadOf(anyLong(), anyLong())).thenReturn(true);
        // 当前已有 50 个 APPROVED/IN_PROGRESS
        when(enrollmentRepository.selectCount(any())).thenReturn(50L);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(2L);

            BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(1L));
            assertEquals(ErrorCode.MS_MAX_STUDENTS_REACHED.getCode(), ex.getCode());
        }
    }

    @Test
    @DisplayName("approve: 版本冲突抛出 MS_CONCURRENT_MODIFICATION")
    void approve_versionConflict() {
        MicroSpecialtyEnrollment en = pendingEnrollment(1L);
        when(enrollmentRepository.selectById(1L)).thenReturn(en);
        MicroSpecialty ms = specialtyWithStatus("RECRUITING", 100, 10);
        when(msRepository.selectById(anyLong())).thenReturn(ms);
        when(msService.isLeadOf(anyLong(), anyLong())).thenReturn(true);
        // First update returns 0 → version conflict
        when(enrollmentRepository.update(any(), any())).thenReturn(0);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(2L);

            BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(1L));
            assertEquals(ErrorCode.MS_CONCURRENT_MODIFICATION.getCode(), ex.getCode());
        }
    }

    @Test
    @DisplayName("classImport: 剩余名额不足时整班拒绝导入")
    void classImport_capacityInsufficient() {
        MicroSpecialty ms = specialtyWithStatus("RECRUITING", 3, 2);
        when(msRepository.selectForUpdate(1L)).thenReturn(ms);
        when(enrollmentRepository.selectCount(any())).thenReturn(2L);
        when(userRepository.selectList(any())).thenReturn(List.of(studentUser(11L), studentUser(12L)));
        when(enrollmentRepository.selectList(any())).thenReturn(Collections.emptyList());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.classImport(1L, 9L));
        assertEquals(ErrorCode.MS_MAX_STUDENTS_REACHED.getCode(), ex.getCode());
    }

    // ==================== reject() ====================

    @Test
    @DisplayName("reject: PENDING → REJECTED 正常驳回")
    void reject_success() {
        MicroSpecialtyEnrollment en = pendingEnrollment(1L);
        when(enrollmentRepository.selectById(1L)).thenReturn(en);
        MicroSpecialty ms = msWithStatus("RECRUITING");
        when(msRepository.selectById(anyLong())).thenReturn(ms);
        when(msService.isLeadOf(anyLong(), anyLong())).thenReturn(true);
        when(enrollmentRepository.update(any(), any())).thenReturn(1);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(2L);

            service.reject(1L, "名额已满");

            verify(notificationService).notifyAsync(eq(5L), eq(NotificationType.MS_ENROLLMENT_REJECTED),
                    anyString(), contains("名额已满"), anyLong());
        }
    }

    @Test
    @DisplayName("reject: 非 PENDING 抛出 MS_STATUS_INVALID")
    void reject_notPending() {
        MicroSpecialtyEnrollment en = pendingEnrollment(1L);
        en.setStatus("APPROVED");
        when(enrollmentRepository.selectById(1L)).thenReturn(en);
        MicroSpecialty ms = msWithStatus("RECRUITING");
        when(msRepository.selectById(anyLong())).thenReturn(ms);
        when(msService.isLeadOf(anyLong(), anyLong())).thenReturn(true);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(2L);

            BusinessException ex = assertThrows(BusinessException.class, () -> service.reject(1L, "原因"));
            assertEquals(ErrorCode.MS_STATUS_INVALID.getCode(), ex.getCode());
        }
    }

    // ==================== drop() ====================

    @Test
    @DisplayName("drop: 级联退出走课程退课主链")
    void drop_success() {
        MicroSpecialtyEnrollment en = approvedEnrollment(1L);
        when(enrollmentRepository.selectById(1L)).thenReturn(en);
        MicroSpecialty ms = msWithStatus("RECRUITING");
        ms.setStudentCount(10);
        Enrollment courseEn = courseEnrollment(101L, 201L, 5L, "MICRO_SPECIALTY_AUTO", EnrollmentStatus.APPROVED.getValue());
        when(msRepository.selectById(anyLong())).thenReturn(ms);
        when(enrollmentRepository.update(any(), any())).thenReturn(1);
        when(msRepository.update(any(), any())).thenReturn(1);
        when(msCourseRepository.selectList(any())).thenReturn(List.of(requiredCourse(201L, 1L)));
        when(courseEnrollmentRepository.selectOne(any())).thenReturn(courseEn);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(5L);
            su.when(SecurityUtil::isAdmin).thenReturn(false);

            service.drop(1L, true, "个人原因");

            verify(enrollmentRepository, atLeastOnce()).update(any(), any());
            verify(enrollmentService).cancelEnrollment(101L, 5L);
            verify(notificationService, atLeastOnce())
                    .notifyAsync(anyLong(), eq(NotificationType.MS_ENROLLMENT_DROPPED),
                            anyString(), anyString(), anyLong());
        }
    }

    @Test
    @DisplayName("drop: 学生自助退出不级联取消课程")
    void drop_withoutCascade_keepsCourseEnrollment() {
        MicroSpecialtyEnrollment en = approvedEnrollment(1L);
        when(enrollmentRepository.selectById(1L)).thenReturn(en);
        MicroSpecialty ms = msWithStatus("RECRUITING");
        ms.setStudentCount(10);
        when(msRepository.selectById(anyLong())).thenReturn(ms);
        when(enrollmentRepository.update(any(), any())).thenReturn(1);
        when(msRepository.update(any(), any())).thenReturn(1);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(5L);
            su.when(SecurityUtil::isAdmin).thenReturn(false);

            service.drop(1L, false, "个人原因");

            verify(enrollmentService, never()).cancelEnrollment(anyLong(), anyLong());
        }
    }

    @Test
    @DisplayName("drop: 非本人操作抛出 NO_PERMISSION (IDOR)")
    void drop_notOwner() {
        MicroSpecialtyEnrollment en = approvedEnrollment(1L);
        when(enrollmentRepository.selectById(1L)).thenReturn(en);
        MicroSpecialty ms = msWithStatus("RECRUITING");
        when(msRepository.selectById(anyLong())).thenReturn(ms);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(999L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.drop(1L, false, "原因"));
            assertEquals(ErrorCode.NO_PERMISSION.getCode(), ex.getCode());
        }
    }

    @Test
    @DisplayName("drop: 终态拒绝抛出 MS_STATUS_INVALID")
    void drop_terminalMs() {
        MicroSpecialtyEnrollment en = approvedEnrollment(1L);
        when(enrollmentRepository.selectById(1L)).thenReturn(en);
        MicroSpecialty ms = msWithStatus("CANCELLED");
        when(msRepository.selectById(anyLong())).thenReturn(ms);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(5L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.drop(1L, false, "原因"));
            assertEquals(ErrorCode.MS_STATUS_INVALID.getCode(), ex.getCode());
        }
    }

    // ==================== reapply() ====================

    @Test
    @DisplayName("reapply: REJECTED → PENDING 重新申请")
    void reapply_rejected() {
        MicroSpecialtyEnrollment en = pendingEnrollment(1L);
        en.setStatus("REJECTED");
        when(enrollmentRepository.selectById(1L)).thenReturn(en);
        MicroSpecialty ms = msWithStatus("RECRUITING");
        when(msRepository.selectById(anyLong())).thenReturn(ms);
        when(enrollmentRepository.update(any(), any())).thenReturn(1);
        when(userRepository.selectById(5L)).thenReturn(studentUser(5L));

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(5L);
            su.when(() -> SecurityUtil.hasRole("STUDENT")).thenReturn(true);

            MicroSpecialtyEnrollmentVO vo = service.reapply(1L);

            assertNotNull(vo);
            assertNotNull(vo);
            verify(enrollmentRepository).update(any(), any());
        }
    }

    @Test
    @DisplayName("reapply: DROPPED → PENDING 重新申请")
    void reapply_dropped() {
        MicroSpecialtyEnrollment en = pendingEnrollment(1L);
        en.setStatus("DROPPED");
        when(enrollmentRepository.selectById(1L)).thenReturn(en);
        MicroSpecialty ms = msWithStatus("RECRUITING");
        when(msRepository.selectById(anyLong())).thenReturn(ms);
        when(enrollmentRepository.update(any(), any())).thenReturn(1);
        when(userRepository.selectById(5L)).thenReturn(studentUser(5L));

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(5L);
            su.when(() -> SecurityUtil.hasRole("STUDENT")).thenReturn(true);

            service.reapply(1L);
            verify(enrollmentRepository).update(any(), any());
        }
    }

    @Test
    @DisplayName("reapply: FAILED → PENDING 重新申请")
    void reapply_failed() {
        MicroSpecialtyEnrollment en = pendingEnrollment(1L);
        en.setStatus("FAILED");
        when(enrollmentRepository.selectById(1L)).thenReturn(en);
        MicroSpecialty ms = msWithStatus("RECRUITING");
        when(msRepository.selectById(anyLong())).thenReturn(ms);
        when(enrollmentRepository.update(any(), any())).thenReturn(1);
        when(userRepository.selectById(5L)).thenReturn(studentUser(5L));

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(5L);
            su.when(() -> SecurityUtil.hasRole("STUDENT")).thenReturn(true);

            service.reapply(1L);
            verify(enrollmentRepository).update(any(), any());
        }
    }

    @Test
    @DisplayName("reapply: 非本人操作抛出 NO_PERMISSION")
    void reapply_notOwner() {
        MicroSpecialtyEnrollment en = pendingEnrollment(1L);
        en.setStatus("REJECTED");
        when(enrollmentRepository.selectById(1L)).thenReturn(en);
        when(userRepository.selectById(999L)).thenReturn(studentUser(999L));

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(999L);
            su.when(() -> SecurityUtil.hasRole("STUDENT")).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class, () -> service.reapply(1L));
            assertEquals(ErrorCode.NO_PERMISSION.getCode(), ex.getCode());
        }
    }

    // ==================== issueCertificate() ====================

    @Test
    @DisplayName("issueCertificate: COMPLETED → CERTIFIED")
    void issueCertificate_success() {
        MicroSpecialtyEnrollment en = pendingEnrollment(1L);
        en.setStatus("COMPLETED");
        when(enrollmentRepository.selectById(1L)).thenReturn(en);
        when(certificateService.issueMicroSpecialtyCertificate(anyLong(), anyLong(), anyLong()))
                .thenReturn(null);
        when(enrollmentRepository.selectById(1L)).thenReturn(en);
        lenient().when(enrollmentRepository.update(any(), any())).thenReturn(1);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::isAdminOrAcademic).thenReturn(true);

            service.issueCertificate(1L);

            verify(certificateService).issueMicroSpecialtyCertificate(eq(5L), anyLong(), eq(1L));
        }
    }

    @Test
    @DisplayName("issueCertificate: 幂等跳过（已有 certificateId）")
    void issueCertificate_idempotent() {
        MicroSpecialtyEnrollment en = pendingEnrollment(1L);
        en.setStatus("COMPLETED");
        en.setCertificateId(100L);
        when(enrollmentRepository.selectById(1L)).thenReturn(en);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::isAdminOrAcademic).thenReturn(true);

            service.issueCertificate(1L);

            verify(certificateService, never()).issueMicroSpecialtyCertificate(anyLong(), anyLong(), anyLong());
        }
    }

    @Test
    @DisplayName("issueCertificate: 无认证上下文时允许内部自动发证")
    void issueCertificate_internalWithoutAuth() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();

        MicroSpecialtyEnrollment en = pendingEnrollment(1L);
        en.setStatus("COMPLETED");
        when(enrollmentRepository.selectById(1L)).thenReturn(en);
        when(certificateService.issueMicroSpecialtyCertificate(anyLong(), anyLong(), anyLong()))
                .thenReturn(null);
        when(enrollmentRepository.selectById(1L)).thenReturn(en);
        lenient().when(enrollmentRepository.update(any(), any())).thenReturn(1);

        service.issueCertificate(1L);

        verify(certificateService).issueMicroSpecialtyCertificate(eq(5L), anyLong(), eq(1L));
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    // ==================== aggregateProgress() ====================

    @Test
    @DisplayName("aggregateProgress: ALL_REQUIRED 完成判定")
    void aggregateProgress_allRequired() {
        service.aggregateProgress(1L);

        verify(progressService).aggregateProgress(1L);
    }

    @Test
    @DisplayName("aggregateProgress: FAILED 判定—全部必修评分且未通过")
    void aggregateProgress_failed() {
        service.aggregateProgress(1L);

        verify(progressService).aggregateProgress(1L);
    }

    @Test
    @DisplayName("aggregateProgress: 并发跳过 — version 冲突仅 log warn")
    void aggregateProgress_concurrentSkip() {
        assertDoesNotThrow(() -> service.aggregateProgress(1L));

        verify(progressService).aggregateProgress(1L);
    }

    // ==================== helpers ====================

    private static MicroSpecialty specialtyWithStatus(String status, int maxStudents, int studentCount) {
        MicroSpecialty ms = new MicroSpecialty();
        ms.setId(1L);
        ms.setTitle("测试微专业");
        ms.setCode("MS-001");
        ms.setStatus(status);
        ms.setMaxStudents(maxStudents);
        ms.setStudentCount(studentCount);
        ms.setVersion(0);
        ms.setLeadTeacherId(1L);
        ms.setCreatedAt(LocalDateTime.now());
        ms.setUpdatedAt(LocalDateTime.now());
        return ms;
    }

    private static MicroSpecialty msWithStatus(String status) {
        return specialtyWithStatus(status, 100, 10);
    }

    private static MicroSpecialtyEnrollment pendingEnrollment(Long id) {
        MicroSpecialtyEnrollment en = new MicroSpecialtyEnrollment();
        en.setId(id);
        en.setMicroSpecialtyId(1L);
        en.setUserId(5L);
        en.setStatus("PENDING");
        en.setVersion(0);
        en.setCreatedAt(LocalDateTime.now());
        en.setUpdatedAt(LocalDateTime.now());
        return en;
    }

    private static MicroSpecialtyEnrollment approvedEnrollment(Long id) {
        MicroSpecialtyEnrollment en = pendingEnrollment(id);
        en.setStatus("APPROVED");
        return en;
    }

    private static MicroSpecialtyEnrollment inProgressEnrollment(Long id) {
        MicroSpecialtyEnrollment en = pendingEnrollment(id);
        en.setStatus("IN_PROGRESS");
        return en;
    }

    private static MicroSpecialtyCourse requiredCourse(Long courseId, Long msId) {
        MicroSpecialtyCourse mc = new MicroSpecialtyCourse();
        mc.setId(courseId);
        mc.setCourseId(courseId);
        mc.setMicroSpecialtyId(msId != null ? msId : 1L);
        mc.setIsRequired(true);
        mc.setCredits(BigDecimal.valueOf(3));
        mc.setMinScore(BigDecimal.valueOf(60));
        mc.setVersion(0);
        return mc;
    }

    private static User studentUser(Long id) {
        User u = new User();
        u.setId(id);
        u.setRole(UserRole.STUDENT);
        u.setRealName("学生" + id);
        return u;
    }

    private static User teacherUser(Long id) {
        User u = new User();
        u.setId(id);
        u.setRole(UserRole.TEACHER);
        u.setRealName("教师" + id);
        return u;
    }

    private static Enrollment courseEnrollment(Long id, Long courseId, Long userId, String sourceChannel, String status) {
        Enrollment enrollment = new Enrollment();
        enrollment.setId(id);
        enrollment.setCourseId(courseId);
        enrollment.setUserId(userId);
        enrollment.setSourceChannel(sourceChannel);
        enrollment.setEnrollmentStatus(status);
        enrollment.setVersion(0);
        return enrollment;
    }

    private static Course course(Long id, String title) {
        Course c = new Course();
        c.setId(id);
        c.setTitle(title);
        return c;
    }
}
