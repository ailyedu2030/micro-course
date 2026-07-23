package com.microcourse.service;

import com.microcourse.entity.Department;
import com.microcourse.entity.MicroSpecialtyProposal;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.ChapterTeacherAssignmentRepository;
import com.microcourse.repository.DepartmentRepository;
import com.microcourse.repository.MicroSpecialtyProposalRepository;
import com.microcourse.repository.ProposalCourseRepository;
import com.microcourse.repository.ProposalLeadCourseRepository;
import com.microcourse.repository.ProposalSharedUnitRepository;
import com.microcourse.repository.ProposalSignatureRepository;
import com.microcourse.repository.ProposalTeamMemberRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.impl.StorageApplicationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageApplicationServiceTest {

    @Mock private MicroSpecialtyProposalRepository proposalRepository;
    @Mock private ProposalCourseRepository courseRepository;
    @Mock private ProposalLeadCourseRepository leadCourseRepository;
    @Mock private ProposalTeamMemberRepository teamMemberRepository;
    @Mock private ProposalSignatureRepository signatureRepository;
    @Mock private ProposalSharedUnitRepository sharedUnitRepository;
    @Mock private UserRepository userRepository;
    @Mock private ChapterTeacherAssignmentRepository assignmentRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private StorageApplicationQueryService queryService;
    @Mock private StorageApplicationCudService cudService;
    @Mock private NotificationService notificationService;
    @Mock private MicroSpecialtyProposalService msProposalService;

    @InjectMocks
    private StorageApplicationServiceImpl storageApplicationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("initDraft uses teacher department instead of fallback department")
    void initDraftUsesTeacherDepartment() {
        User teacher = new User();
        teacher.setId(7L);
        teacher.setDepartmentId(9L);
        Department department = new Department();
        department.setId(9L);

        when(userRepository.selectById(7L)).thenReturn(teacher);
        when(departmentRepository.selectById(9L)).thenReturn(department);
        when(proposalRepository.insert(any(MicroSpecialtyProposal.class))).thenAnswer(invocation -> {
            MicroSpecialtyProposal proposal = invocation.getArgument(0);
            proposal.setId(101L);
            return 1;
        });

        Long proposalId = storageApplicationService.initDraft(7L);

        ArgumentCaptor<MicroSpecialtyProposal> proposalCaptor = ArgumentCaptor.forClass(MicroSpecialtyProposal.class);
        verify(proposalRepository).insert(proposalCaptor.capture());
        assertEquals(101L, proposalId);
        assertEquals(9L, proposalCaptor.getValue().getOfferDepartmentId());
        verify(signatureRepository, times(3)).insert(any());
    }

    @Test
    @DisplayName("initDraft rejects teacher without department and does not persist proposal")
    void initDraftRejectsTeacherWithoutDepartment() {
        User teacher = new User();
        teacher.setId(7L);
        teacher.setDepartmentId(null);
        when(userRepository.selectById(7L)).thenReturn(teacher);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> storageApplicationService.initDraft(7L));

        assertEquals(ErrorCode.BAD_REQUEST_PARAM.getCode(), ex.getCode());
        assertEquals("当前教师账号未绑定学院，无法初始化申报草稿", ex.getMessage());
        verify(proposalRepository, never()).insert(any(MicroSpecialtyProposal.class));
        verify(signatureRepository, never()).insert(any());
    }
}
