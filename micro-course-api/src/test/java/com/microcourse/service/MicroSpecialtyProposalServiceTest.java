package com.microcourse.service;

import com.microcourse.dto.microSpecialty.MicroSpecialtyProposalRequest;
import com.microcourse.dto.microSpecialty.MicroSpecialtyVO;
import com.microcourse.entity.MicroSpecialty;
import com.microcourse.entity.MicroSpecialtyProposal;
import com.microcourse.entity.MicroSpecialtyTeacher;
import com.microcourse.enums.NotificationType;
import com.microcourse.repository.*;
import com.microcourse.service.impl.MicroSpecialtyProposalServiceImpl;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MicroSpecialtyProposalService — 微专业申报")
class MicroSpecialtyProposalServiceTest {

    @Mock private MicroSpecialtyProposalRepository proposalRepository;
    @Mock private MicroSpecialtyRepository msRepository;
    @Mock private MicroSpecialtyTeacherRepository msTeacherRepository;
    @Mock private NotificationService notificationService;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private MicroSpecialtyMaterializationService materializationService;

    private MicroSpecialtyProposalServiceImpl service;

    @BeforeAll
    static void init() {
        MybatisPlusTestHelper.initTableInfo();
    }

    @BeforeEach
    void setUp() {
        service = new MicroSpecialtyProposalServiceImpl(
                proposalRepository, msRepository, msTeacherRepository,
                notificationService, departmentRepository, userRepository,
                materializationService);
    }

    // ==================== submitProposal() ====================

    @Test
    @DisplayName("submitProposal: 正常提交申报 → PENDING_REVIEW + 返回 ID")
    void submitProposal_success() {
        MicroSpecialtyProposalRequest req = new MicroSpecialtyProposalRequest();
        req.setTitle("人工智能微专业");
        req.setDescription("AI 方向");
        req.setOfferDepartmentId(1L);
        req.setSemester("2026秋季");
        req.setMaxStudents(60);

        doAnswer(inv -> {
            MicroSpecialtyProposal p = inv.getArgument(0);
            p.setId(100L);
            return 1;
        }).when(proposalRepository).insert(any(MicroSpecialtyProposal.class));

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(10L);

            Long id = service.submitProposal(req);

            assertNotNull(id);
            assertEquals(100L, id);
            verify(proposalRepository).insert(any(MicroSpecialtyProposal.class));
        }
    }

    // ==================== approveProposal() ====================

    @Test
    @DisplayName("approveProposal: PENDING_REVIEW → APPROVED + 自动创建 DRAFT")
    void approveProposal_success() {
        MicroSpecialtyProposal proposal = new MicroSpecialtyProposal();
        proposal.setId(100L);
        proposal.setTitle("人工智能微专业");
        proposal.setDescription("AI 方向");
        proposal.setOfferDepartmentId(1L);
        proposal.setProposerId(10L);
        proposal.setSemester("2026秋季");
        proposal.setMaxStudents(60);
        proposal.setStatus("PENDING_REVIEW");
        proposal.setVersion(0); // AC04: 乐观锁必须
        when(proposalRepository.selectById(100L)).thenReturn(proposal);
        // 使用 Answer 让 update() 修改 proposal 对象状态，确保 re-select 后状态正确
        when(proposalRepository.update(any(), any())).thenAnswer(inv -> {
            proposal.setStatus("APPROVED");
            return 1;
        });
        doAnswer(inv -> {
            MicroSpecialty ms = inv.getArgument(0);
            ms.setId(200L);
            return 1;
        }).when(msRepository).insert(any(MicroSpecialty.class));
        lenient().when(msTeacherRepository.insert(any())).thenReturn(1);
        lenient().when(proposalRepository.updateById(any())).thenReturn(1);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(1L);

            MicroSpecialtyVO vo = service.approveProposal(100L);

            assertNotNull(vo);
            assertEquals(200L, vo.getId());
            assertEquals("APPROVED", proposal.getStatus());
            verify(msRepository).insert(any(MicroSpecialty.class));
            verify(msTeacherRepository).insert(any(MicroSpecialtyTeacher.class));
            verify(notificationService, times(2)).notifyAsync(
                    eq(10L), any(NotificationType.class), anyString(), anyString(), anyLong());
        }
    }

    // ==================== rejectProposal() ====================

    @Test
    @DisplayName("rejectProposal: PENDING_REVIEW → REJECTED")
    void rejectProposal_success() {
        MicroSpecialtyProposal proposal = new MicroSpecialtyProposal();
        proposal.setId(100L);
        proposal.setTitle("人工智能微专业");
        proposal.setProposerId(10L);
        proposal.setStatus("PENDING_REVIEW");
        proposal.setVersion(0); // AC04: 乐观锁必须
        when(proposalRepository.selectById(100L)).thenReturn(proposal);
        // 使用 Answer 让 update() 修改 proposal 对象状态
        when(proposalRepository.update(any(), any())).thenAnswer(inv -> {
            proposal.setStatus("REJECTED");
            proposal.setReviewComment("材料不全");
            return 1;
        });

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(1L);

            service.rejectProposal(100L, "材料不全");

            assertEquals("REJECTED", proposal.getStatus());
            assertEquals("材料不全", proposal.getReviewComment());
            verify(proposalRepository).update(any(), any());
            verify(notificationService).notifyAsync(eq(10L), eq(NotificationType.MS_PROPOSAL_REJECTED),
                    anyString(), contains("材料不全"), isNull());
        }
    }

    // ==================== withdrawProposal() ====================

    @Test
    @DisplayName("withdrawProposal: PENDING_REVIEW → WITHDRAWN")
    void withdrawProposal_success() {
        MicroSpecialtyProposal proposal = new MicroSpecialtyProposal();
        proposal.setId(100L);
        proposal.setProposerId(10L);
        proposal.setStatus("PENDING_REVIEW");
        when(proposalRepository.selectById(100L)).thenReturn(proposal);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(10L);

            service.withdrawProposal(100L);

            assertEquals("WITHDRAWN", proposal.getStatus());
            verify(proposalRepository).updateById(any());
        }
    }

    // ==================== resubmitProposal() ====================

    @Test
    @DisplayName("resubmitProposal: REJECTED → PENDING_REVIEW 重提")
    void resubmitProposal_success() {
        MicroSpecialtyProposal proposal = new MicroSpecialtyProposal();
        proposal.setId(100L);
        proposal.setProposerId(10L);
        proposal.setStatus("REJECTED");
        when(proposalRepository.selectById(100L)).thenReturn(proposal);

        MicroSpecialtyProposalRequest req = new MicroSpecialtyProposalRequest();
        req.setTitle("人工智能微专业 v2");
        req.setDescription("补充后的描述");

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(10L);

            service.resubmitProposal(100L, req);

            assertEquals("PENDING_REVIEW", proposal.getStatus());
            assertEquals("人工智能微专业 v2", proposal.getTitle());
            verify(proposalRepository).updateById(any());
        }
    }
}
