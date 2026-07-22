package com.microcourse.service;

import com.microcourse.dto.microSpecialty.MicroSpecialtyCreateRequest;
import com.microcourse.dto.microSpecialty.MicroSpecialtyLeadTransferRequest;
import com.microcourse.dto.microSpecialty.MicroSpecialtyVO;
import com.microcourse.entity.MicroSpecialty;
import com.microcourse.entity.MicroSpecialtyTeacher;
import com.microcourse.entity.User;
import com.microcourse.enums.NotificationType;
import com.microcourse.enums.UserRole;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.ChapterTeacherAssignmentRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.MicroSpecialtyCourseRepository;
import com.microcourse.repository.MicroSpecialtyEnrollmentRepository;
import com.microcourse.repository.MicroSpecialtyFeaturedAuditRepository;
import com.microcourse.repository.MicroSpecialtyProposalRepository;
import com.microcourse.repository.MicroSpecialtyRepository;
import com.microcourse.repository.MicroSpecialtyTeacherRepository;
import com.microcourse.repository.ProposalChapterRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.impl.MicroSpecialtyServiceImpl;
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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MicroSpecialtyService — 微专业主表状态流转")
class MicroSpecialtyServiceTest {

    @Mock private CourseRepository courseRepository;
    @Mock private MicroSpecialtyRepository msRepository;
    @Mock private MicroSpecialtyCourseRepository msCourseRepository;
    @Mock private MicroSpecialtyTeacherRepository msTeacherRepository;
    @Mock private MicroSpecialtyEnrollmentRepository msEnrollmentRepository;
    @Mock private MicroSpecialtyProposalRepository msProposalRepository;
    @Mock private UserRepository userRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private NotificationService notificationService;
    @Mock private MicroSpecialtyEnrollmentService msEnrollmentService;
    @Mock private MicroSpecialtyFeaturedAuditRepository msFeaturedAuditRepository;
    @Mock private MicroSpecialtyFeaturedService featuredService;
    @Mock private ProposalChapterRepository proposalChapterRepository;
    @Mock private ChapterTeacherAssignmentRepository chapterAssignRepo;
    @Mock private MicroSpecialtyQueryService queryService;
    @Mock private MicroSpecialtyAdminService adminService;

    private MicroSpecialtyServiceImpl service;

    @BeforeAll
    static void init() {
        MybatisPlusTestHelper.initTableInfo();
    }

    @BeforeEach
    void setUp() {
        service = new MicroSpecialtyServiceImpl(
                courseRepository, msRepository, msCourseRepository, msTeacherRepository,
                msEnrollmentRepository, notificationService,
                proposalChapterRepository, chapterAssignRepo,
                queryService, adminService,
                userRepository);
    }

    // ==================== create() ====================

    @Test
    @DisplayName("create: 正常创建 DRAFT 状态微专业")
    void create_success() {
        MicroSpecialtyCreateRequest req = createBasicRequest();
        when(msRepository.selectCount(any())).thenReturn(0L);
        doAnswer(inv -> {
            MicroSpecialty ms = inv.getArgument(0);
            ms.setId(1L);
            return 1;
        }).when(msRepository).insert(any(MicroSpecialty.class));
        MicroSpecialtyVO mockVO = new MicroSpecialtyVO();
        mockVO.setId(1L);
        mockVO.setTitle("测试微专业");
        mockVO.setCode("MS-001");
        lenient().when(queryService.toVO(any())).thenReturn(mockVO);
        lenient().when(msCourseRepository.selectCount(any())).thenReturn(0L);
        lenient().when(msEnrollmentRepository.selectCount(any())).thenReturn(0L);
        lenient().when(msTeacherRepository.selectCount(any())).thenReturn(0L);

        // adminService void methods: default no-op, failure-path tests override via doThrow
        lenient().doNothing().when(adminService).submit(anyLong());
        lenient().doNothing().when(adminService).approve(anyLong());
        lenient().doNothing().when(adminService).reject(anyLong(), anyString());
        lenient().doNothing().when(adminService).open(anyLong());
        lenient().doNothing().when(adminService).close(anyLong());
        lenient().doNothing().when(adminService).cancel(anyLong(), anyString());
        lenient().doNothing().when(adminService).archive(anyLong());
        lenient().doNothing().when(adminService).transferLeadership(anyLong(), any());
        lenient().doNothing().when(adminService).requireLeadOf(anyLong());
        lenient().doNothing().when(adminService).checkNotTerminal(any());

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(2L);

            MicroSpecialtyVO vo = service.create(req);

            assertNotNull(vo);
            verify(msRepository).insert(any(MicroSpecialty.class));
            verify(msTeacherRepository).insert(any(MicroSpecialtyTeacher.class));
            verify(notificationService).notifyAsync(eq(1L), eq(NotificationType.MS_INVITE_LEAD),
                    anyString(), anyString(), anyLong());
        }
    }

    @Test
    @DisplayName("create: 重复 code 抛出 MICRO_SPECIALTY_CODE_EXISTS")
    void create_duplicateCode() {
        MicroSpecialtyCreateRequest req = createBasicRequest();
        when(msRepository.selectCount(any())).thenReturn(1L);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(2L);
            BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req));
            assertEquals(ErrorCode.MICRO_SPECIALTY_CODE_EXISTS.getCode(), ex.getCode());
        }
    }

    // ==================== submit() ====================

    @Test
    @DisplayName("submit: DRAFT → PENDING_REVIEW 正常提交")
    void submit_draftToPendingReview() {
        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(2L);
            su.when(SecurityUtil::isAdmin).thenReturn(false);
            su.when(SecurityUtil::isAdminOrAcademic).thenReturn(false);

            service.submit(1L);

            verify(adminService).submit(1L);
        }
    }

    @Test
    @DisplayName("submit: REJECTED → PENDING_REVIEW 重新提交")
    void submit_rejectedToPendingReview() {
        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(2L);
            su.when(SecurityUtil::isAdmin).thenReturn(false);

            service.submit(1L);
            verify(adminService).submit(1L);
        }
    }

    @Test
    @DisplayName("submit: 非法状态抛出 MS_STATUS_INVALID")
    void submit_invalidStatus() {
        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(2L);
            su.when(SecurityUtil::isAdmin).thenReturn(false);

            doThrow(new BusinessException(ErrorCode.MS_STATUS_INVALID)).when(adminService).submit(1L);
            BusinessException ex = assertThrows(BusinessException.class, () -> service.submit(1L));
            assertEquals(ErrorCode.MS_STATUS_INVALID.getCode(), ex.getCode());
        }
    }

    @Test
    @DisplayName("submit: LEAD 未接受抛出 MS_LEAD_REQUIRED")
    void submit_leadNotAccepted() {
        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(2L);
            su.when(SecurityUtil::isAdmin).thenReturn(false);

            doThrow(new BusinessException(ErrorCode.MS_LEAD_REQUIRED)).when(adminService).submit(1L);
            BusinessException ex = assertThrows(BusinessException.class, () -> service.submit(1L));
            assertEquals(ErrorCode.MS_LEAD_REQUIRED.getCode(), ex.getCode());
        }
    }

    @Test
    @DisplayName("submit: 无课程抛出 MS_STATUS_INVALID")
    void submit_noCourses() {
        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(2L);
            su.when(SecurityUtil::isAdmin).thenReturn(false);

            doThrow(new BusinessException(ErrorCode.MS_STATUS_INVALID)).when(adminService).submit(1L);
            BusinessException ex = assertThrows(BusinessException.class, () -> service.submit(1L));
            assertEquals(ErrorCode.MS_STATUS_INVALID.getCode(), ex.getCode());
        }
    }

    // ==================== approve() ====================

    @Test
    @DisplayName("approve: PENDING_REVIEW → APPROVED 正常审批通过")
    void approve_success() {
        service.approve(1L);

        verify(adminService).approve(1L);
    }

    @Test
    @DisplayName("approve: 非 PENDING_REVIEW 抛出 MS_STATUS_INVALID")
    void approve_invalidStatus() {
        doThrow(new BusinessException(ErrorCode.MS_STATUS_INVALID)).when(adminService).approve(1L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(1L));
        assertEquals(ErrorCode.MS_STATUS_INVALID.getCode(), ex.getCode());
    }

    // ==================== reject() ====================

    @Test
    @DisplayName("reject: PENDING_REVIEW → REJECTED 正常驳回")
    void reject_success() {
        service.reject(1L, "内容不符合要求");

        verify(adminService).reject(eq(1L), anyString());
    }

    @Test
    @DisplayName("reject: 非 PENDING_REVIEW 抛出 MS_STATUS_INVALID")
    void reject_invalidStatus() {
        doThrow(new BusinessException(ErrorCode.MS_STATUS_INVALID)).when(adminService).reject(eq(1L), anyString());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.reject(1L, "原因"));
        assertEquals(ErrorCode.MS_STATUS_INVALID.getCode(), ex.getCode());
    }

    // ==================== open() ====================

    @Test
    @DisplayName("open: APPROVED → RECRUITING 正常开课")
    void open_success() {
        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(2L);
            su.when(SecurityUtil::isAdmin).thenReturn(false);

            service.open(1L);

            verify(adminService).open(1L);
        }
    }

    @Test
    @DisplayName("open: 预条件不满足（团队<2）抛出 MS_STATUS_INVALID")
    void open_prerequisitesNotMet() {
        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(2L);
            su.when(SecurityUtil::isAdmin).thenReturn(false);

            doThrow(new BusinessException(ErrorCode.MS_STATUS_INVALID)).when(adminService).open(1L);
            BusinessException ex = assertThrows(BusinessException.class, () -> service.open(1L));
            assertEquals(ErrorCode.MS_STATUS_INVALID.getCode(), ex.getCode());
        }
    }

    // ==================== close() ====================

    @Test
    @DisplayName("close: RECRUITING → COMPLETED 正常结业")
    void close_success() {
        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(2L);
            su.when(SecurityUtil::isAdmin).thenReturn(false);

            service.close(1L);

            verify(adminService).close(1L);
        }
    }

    @Test
    @DisplayName("close: 非 RECRUITING 抛出 MS_STATUS_INVALID")
    void close_invalidStatus() {
        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(2L);
            su.when(SecurityUtil::isAdmin).thenReturn(false);

            doThrow(new BusinessException(ErrorCode.MS_STATUS_INVALID)).when(adminService).close(1L);
            BusinessException ex = assertThrows(BusinessException.class, () -> service.close(1L));
            assertEquals(ErrorCode.MS_STATUS_INVALID.getCode(), ex.getCode());
        }
    }

    // ==================== cancel() ====================

    @Test
    @DisplayName("cancel: 正常取消 + 级联 DROPPED + 审计日志")
    void cancel_success() {
        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(2L);
            su.when(SecurityUtil::isAdmin).thenReturn(false);

            service.cancel(1L, "测试取消原因");

            verify(adminService).cancel(1L, "测试取消原因");
        }
    }

    @Test
    @DisplayName("cancel: 重复取消抛出 MS_STATUS_INVALID")
    void cancel_duplicate() {
        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(2L);
            su.when(SecurityUtil::isAdmin).thenReturn(false);

            doThrow(new BusinessException(ErrorCode.MS_STATUS_INVALID)).when(adminService).cancel(eq(1L), anyString());
            BusinessException ex = assertThrows(BusinessException.class, () -> service.cancel(1L, "测试取消原因"));
            assertEquals(ErrorCode.MS_STATUS_INVALID.getCode(), ex.getCode());
        }
    }

    @Test
    @DisplayName("cancel: ACADEMIC 角色（教务处）可执行取消（修复 P1-C 越级）")
    void cancel_byAcademic_allowed() {
        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(999L);
            su.when(SecurityUtil::isAdmin).thenReturn(false);
            su.when(SecurityUtil::isAdminOrAcademic).thenReturn(true);

            // 不应抛 BusinessException
            service.cancel(1L, "测试取消原因");

            verify(adminService).cancel(eq(1L), anyString());
        }
    }

    // ==================== archive() ====================

    @Test
    @DisplayName("archive: COMPLETED → ARCHIVED 正常归档")
    void archive_success() {
        service.archive(1L);

        verify(adminService).archive(1L);
    }

    @Test
    @DisplayName("archive: 非 COMPLETED 抛出 MS_STATUS_INVALID")
    void archive_invalidStatus() {
        doThrow(new BusinessException(ErrorCode.MS_STATUS_INVALID)).when(adminService).archive(1L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.archive(1L));
        assertEquals(ErrorCode.MS_STATUS_INVALID.getCode(), ex.getCode());
    }

    // ==================== transferLeadership() ====================

    @Test
    @DisplayName("transferLeadership: APPROVED 状态正常继任")
    void transferLeadership_success() {
        MicroSpecialtyLeadTransferRequest req = new MicroSpecialtyLeadTransferRequest();
        req.setNewLeadTeacherId(5L);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(2L);
            su.when(SecurityUtil::isAdmin).thenReturn(true);

            service.transferLeadership(1L, req);

            verify(adminService).transferLeadership(eq(1L), any());
        }
    }

    @Test
    @DisplayName("transferLeadership: 终态拒绝抛出 MS_STATUS_INVALID")
    void transferLeadership_terminalStatus() {
        MicroSpecialtyLeadTransferRequest req = new MicroSpecialtyLeadTransferRequest();
        req.setNewLeadTeacherId(5L);

        doThrow(new BusinessException(ErrorCode.MS_STATUS_INVALID)).when(adminService).transferLeadership(eq(1L), any());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.transferLeadership(1L, req));
        assertEquals(ErrorCode.MS_STATUS_INVALID.getCode(), ex.getCode());
    }

    // ==================== helpers ====================

    private static MicroSpecialtyCreateRequest createBasicRequest() {
        MicroSpecialtyCreateRequest req = new MicroSpecialtyCreateRequest();
        req.setCode("MS-001");
        req.setTitle("测试微专业");
        req.setOfferDepartmentId(1L);
        req.setLeadTeacherId(1L);
        req.setMaxStudents(100);
        req.setSemester("2026秋季");
        return req;
    }

}
