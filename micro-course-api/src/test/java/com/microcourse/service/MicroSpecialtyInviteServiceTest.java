package com.microcourse.service;

import com.microcourse.entity.MicroSpecialty;
import com.microcourse.entity.MicroSpecialtyTeacher;
import com.microcourse.entity.User;
import com.microcourse.enums.NotificationType;
import com.microcourse.enums.UserRole;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.ChapterTeacherAssignmentRepository;
import com.microcourse.repository.MicroSpecialtyRepository;
import com.microcourse.repository.MicroSpecialtyTeacherRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.impl.MicroSpecialtyInviteServiceImpl;
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
@DisplayName("MicroSpecialtyInviteService — 教师邀请")
class MicroSpecialtyInviteServiceTest {

    @Mock private MicroSpecialtyTeacherRepository teacherRepository;
    @Mock private MicroSpecialtyRepository msRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private MicroSpecialtyService msService;
    @Mock private ChapterTeacherAssignmentRepository chapterAssignRepository;

    private MicroSpecialtyInviteServiceImpl service;

    @BeforeAll
    static void init() {
        MybatisPlusTestHelper.initTableInfo();
    }

    @BeforeEach
    void setUp() {
        service = new MicroSpecialtyInviteServiceImpl(
                teacherRepository, msRepository, userRepository,
                notificationService, msService, chapterAssignRepository);
    }

    // ==================== acceptInvite() ====================

    @Test
    @DisplayName("acceptInvite: 同学院 → ACTIVE")
    void acceptInvite_sameDept() {
        MicroSpecialtyTeacher record = invitedRecord(1L, 1L, 5L, "MEMBER");
        when(teacherRepository.selectById(1L)).thenReturn(record);
        MicroSpecialty ms = msWithStatus("RECRUITING");
        ms.setOfferDepartmentId(10L);
        when(msRepository.selectById(anyLong())).thenReturn(ms);
        when(userRepository.selectById(5L)).thenReturn(userWithDept(5L, 10L));
        when(teacherRepository.update(any(), any())).thenReturn(1);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(5L);

            service.acceptInvite(1L);

            verify(teacherRepository).update(any(), any());
            verify(notificationService).notifyAsync(eq(1L), eq(NotificationType.MS_INVITE_ACCEPTED),
                    anyString(), anyString(), anyLong());
        }
    }

    @Test
    @DisplayName("acceptInvite: 跨学院 → PENDING_ACADEMIC")
    void acceptInvite_crossDept() {
        MicroSpecialtyTeacher record = invitedRecord(1L, 1L, 5L, "MEMBER");
        when(teacherRepository.selectById(1L)).thenReturn(record);
        MicroSpecialty ms = msWithStatus("RECRUITING");
        ms.setOfferDepartmentId(10L);
        when(msRepository.selectById(anyLong())).thenReturn(ms);
        when(userRepository.selectById(5L)).thenReturn(userWithDept(5L, 20L)); // different dept
        when(teacherRepository.update(any(), any())).thenReturn(1);
        when(userRepository.selectList(any())).thenReturn(List.of(academicUser(99L)));

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(5L);

            service.acceptInvite(1L);

            verify(notificationService).notifyAsync(eq(99L), eq(NotificationType.MS_INVITE_CROSS_DEPT),
                    anyString(), anyString(), anyLong());
        }
    }

    @Test
    @DisplayName("acceptInvite: 过期拒绝抛出 MS_INVITE_EXPIRED")
    void acceptInvite_expired() {
        MicroSpecialtyTeacher record = invitedRecord(1L, 1L, 5L, "MEMBER");
        record.setInviteExpiresAt(LocalDateTime.now().minusDays(1));
        when(teacherRepository.selectById(1L)).thenReturn(record);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(5L);

            BusinessException ex = assertThrows(BusinessException.class, () -> service.acceptInvite(1L));
            assertEquals(ErrorCode.MS_INVITE_EXPIRED.getCode(), ex.getCode());
        }
    }

    // ==================== declineInvite() ====================

    @Test
    @DisplayName("declineInvite: 正常拒绝 → DECLINED")
    void declineInvite_success() {
        MicroSpecialtyTeacher record = invitedRecord(1L, 1L, 5L, "MEMBER");
        when(teacherRepository.selectById(1L)).thenReturn(record);
        when(teacherRepository.update(any(), any())).thenReturn(1);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(5L);

            service.declineInvite(1L);

            verify(teacherRepository).update(any(), any());
        }
    }

    // ==================== reinviteTeacher() ====================

    @Test
    @DisplayName("reinviteTeacher: REMOVED → INVITED")
    void reinvite_removed() {
        MicroSpecialtyTeacher record = invitedRecord(1L, 1L, 5L, "MEMBER");
        record.setInviteStatus("REMOVED");
        when(teacherRepository.selectById(1L)).thenReturn(record);
        when(msService.isLeadOf(anyLong(), anyLong())).thenReturn(true);
        when(teacherRepository.update(any(), any())).thenReturn(1);
        when(msRepository.selectById(anyLong())).thenReturn(msWithStatus("RECRUITING"));

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(2L);

            service.reinviteTeacher(1L, "MEMBER", "授课", 10L);

            verify(teacherRepository).update(any(), any());
            verify(notificationService).notifyAsync(eq(5L), eq(NotificationType.MS_INVITE_TEAM),
                    anyString(), anyString(), anyLong());
        }
    }

    @Test
    @DisplayName("reinviteTeacher: DECLINED → INVITED")
    void reinvite_declined() {
        MicroSpecialtyTeacher record = invitedRecord(1L, 1L, 5L, "MEMBER");
        record.setInviteStatus("DECLINED");
        when(teacherRepository.selectById(1L)).thenReturn(record);
        when(msService.isLeadOf(anyLong(), anyLong())).thenReturn(true);
        when(teacherRepository.update(any(), any())).thenReturn(1);
        when(msRepository.selectById(anyLong())).thenReturn(msWithStatus("RECRUITING"));

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(2L);

            service.reinviteTeacher(1L, "MEMBER", "授课", 10L);

            verify(teacherRepository).update(any(), any());
        }
    }

    // ==================== leaveTeam() ====================

    @Test
    @DisplayName("leaveTeam: ACTIVE → REMOVED + 通知 LEAD")
    void leaveTeam_success() {
        MicroSpecialtyTeacher record = invitedRecord(1L, 1L, 5L, "MEMBER");
        record.setInviteStatus("ACTIVE");
        when(teacherRepository.selectById(1L)).thenReturn(record);
        MicroSpecialty ms = msWithStatus("RECRUITING");
        ms.setLeadTeacherId(1L);
        when(msRepository.selectById(anyLong())).thenReturn(ms);
        when(teacherRepository.update(any(), any())).thenReturn(1);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(5L);

            service.leaveTeam(1L);

            verify(teacherRepository).update(any(), any());
            verify(notificationService).notifyAsync(eq(1L), eq(NotificationType.MS_TEAM_LEFT),
                    anyString(), anyString(), anyLong());
        }
    }

    // ==================== reviewCrossDept() ====================

    @Test
    @DisplayName("reviewCrossDept: PENDING_ACADEMIC → ACTIVE")
    void reviewCrossDept_approve() {
        MicroSpecialtyTeacher record = invitedRecord(1L, 1L, 5L, "MEMBER");
        record.setInviteStatus("PENDING_ACADEMIC");
        when(teacherRepository.selectById(1L)).thenReturn(record);
        when(msRepository.selectById(anyLong())).thenReturn(msWithStatus("RECRUITING"));
        when(teacherRepository.update(any(), any())).thenReturn(1);
        when(userRepository.selectById(5L)).thenReturn(userWithDept(5L, 10L));

        service.reviewCrossDept(1L, true, null);

        verify(teacherRepository).update(any(), any());
        verify(notificationService).notifyAsync(eq(5L), eq(NotificationType.MS_INVITE_CROSS_DEPT),
                anyString(), contains("已通过"), anyLong());
    }

    // ==================== scanExpired() ====================

    @Test
    @DisplayName("scanExpired: 7天过期 + LEAD 告警")
    void scanExpired_success() {
        MicroSpecialtyTeacher expired = invitedRecord(1L, 1L, 5L, "LEAD");
        expired.setInviteExpiresAt(LocalDateTime.now().minusDays(1));
        when(teacherRepository.selectList(any())).thenReturn(List.of(expired), Collections.emptyList());
        when(teacherRepository.update(any(), any())).thenReturn(1);
        MicroSpecialty ms = msWithStatus("RECRUITING");
        ms.setLeadTeacherId(1L);
        when(msRepository.selectById(anyLong())).thenReturn(ms);
        when(userRepository.selectList(any())).thenReturn(List.of(academicUser(99L)));

        int count = service.scanExpired();

        assertEquals(1, count);
        verify(notificationService).notifyAsync(eq(1L), eq(NotificationType.MS_INVITE_EXPIRED),
                anyString(), anyString(), anyLong());
        verify(notificationService).notifyAsync(eq(99L), eq(NotificationType.MS_INVITE_EXPIRED),
                anyString(), anyString(), anyLong());
    }

    // ==================== helpers ====================

    private static MicroSpecialtyTeacher invitedRecord(Long id, Long msId, Long teacherId, String role) {
        MicroSpecialtyTeacher t = new MicroSpecialtyTeacher();
        t.setId(id);
        t.setMicroSpecialtyId(msId);
        t.setTeacherId(teacherId);
        t.setRole(role);
        t.setInviteStatus("INVITED");
        t.setInvitedBy(1L);
        t.setInvitedAt(LocalDateTime.now());
        t.setInviteExpiresAt(LocalDateTime.now().plusDays(7));
        t.setVersion(0);
        t.setCreatedAt(LocalDateTime.now());
        return t;
    }

    private static MicroSpecialty msWithStatus(String status) {
        MicroSpecialty ms = new MicroSpecialty();
        ms.setId(1L);
        ms.setTitle("测试微专业");
        ms.setStatus(status);
        ms.setLeadTeacherId(1L);
        ms.setVersion(0);
        ms.setCreatedAt(LocalDateTime.now());
        ms.setUpdatedAt(LocalDateTime.now());
        return ms;
    }

    private static User userWithDept(Long id, Long deptId) {
        User u = new User();
        u.setId(id);
        u.setRealName("用户" + id);
        u.setDepartmentId(deptId);
        return u;
    }

    private static User academicUser(Long id) {
        User u = new User();
        u.setId(id);
        u.setRole(UserRole.ACADEMIC);
        u.setRealName("教务" + id);
        return u;
    }
}
