package com.microcourse.service;

import com.microcourse.entity.MicroSpecialty;
import com.microcourse.enums.NotificationType;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.MicroSpecialtyRepository;
import com.microcourse.service.impl.MicroSpecialtyFeaturedServiceImpl;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MicroSpecialtyFeaturedService — 置顶/金标管理")
class MicroSpecialtyFeaturedServiceTest {

    @Mock private MicroSpecialtyRepository msRepository;
    @Mock private NotificationService notificationService;
    @Mock private MicroSpecialtyService msService;

    private MicroSpecialtyFeaturedServiceImpl service;

    @BeforeAll
    static void init() {
        MybatisPlusTestHelper.initTableInfo();
    }

    @BeforeEach
    void setUp() {
        service = new MicroSpecialtyFeaturedServiceImpl(msRepository, notificationService, msService);
    }

    // ==================== applyFeatured() ====================

    @Test
    @DisplayName("applyFeatured: 正常申请置顶 → PENDING")
    void applyFeatured_success() {
        MicroSpecialty ms = msWithStatus("RECRUITING");
        ms.setFeaturedStatus("NONE");
        when(msRepository.selectById(1L)).thenReturn(ms);
        when(msService.isLeadOf(anyLong(), anyLong())).thenReturn(true);
        when(msRepository.update(any(), any())).thenReturn(1);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(2L);

            service.applyFeatured(1L, "质量优秀");

            verify(msRepository).update(any(), any());
        }
    }

    // ==================== approveFeatured() ====================

    @Test
    @DisplayName("approveFeatured: PENDING → APPROVED")
    void approveFeatured_success() {
        MicroSpecialty ms = msWithStatus("RECRUITING");
        ms.setFeaturedStatus("PENDING");
        ms.setLeadTeacherId(1L);
        when(msRepository.selectById(1L)).thenReturn(ms);
        when(msRepository.update(any(), any())).thenReturn(1);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(3L);

            service.approveFeatured(1L);

            verify(msRepository).update(any(), any());
            verify(notificationService).notifyAsync(eq(1L), eq(NotificationType.MS_FEATURED_APPROVED),
                    anyString(), anyString(), eq(1L));
        }
    }

    // ==================== rejectFeatured() ====================

    @Test
    @DisplayName("rejectFeatured: PENDING → REJECTED")
    void rejectFeatured_success() {
        MicroSpecialty ms = msWithStatus("RECRUITING");
        ms.setFeaturedStatus("PENDING");
        ms.setLeadTeacherId(1L);
        when(msRepository.selectById(1L)).thenReturn(ms);
        when(msRepository.update(any(), any())).thenReturn(1);

        service.rejectFeatured(1L, "质量不足");

        verify(msRepository).update(any(), any());
        verify(notificationService).notifyAsync(eq(1L), eq(NotificationType.MS_FEATURED_REJECTED),
                anyString(), contains("质量不足"), eq(1L));
    }

    // ==================== unsetFeatured() ====================

    @Test
    @DisplayName("unsetFeatured: APPROVED → NONE 取消置顶")
    void unsetFeatured_success() {
        MicroSpecialty ms = msWithStatus("RECRUITING");
        ms.setFeaturedStatus("APPROVED");
        when(msRepository.selectById(1L)).thenReturn(ms);
        when(msRepository.update(any(), any())).thenReturn(1);

        service.unsetFeatured(1L);

        verify(msRepository).update(any(), any());
    }

    // ==================== setGoldFeatured() ====================

    @Test
    @DisplayName("setGoldFeatured: 正常设置金标（< 2 个）")
    void setGoldFeatured_success() {
        MicroSpecialty ms = msWithStatus("RECRUITING");
        lenient().doNothing().when(msRepository).acquireGoldFeaturedLock();
        when(msRepository.selectForUpdate(1L)).thenReturn(ms);
        when(msRepository.selectCount(any())).thenReturn(1L);
        when(msRepository.update(any(), any())).thenReturn(1);

        try (MockedStatic<SecurityUtil> su = Mockito.mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(3L);

            service.setGoldFeatured(1L);

            verify(msRepository).update(any(), any());
        }
    }

    @Test
    @DisplayName("setGoldFeatured: 超 2 个限制抛出 MS_GOLD_LIMIT")
    void setGoldFeatured_limitExceeded() {
        MicroSpecialty ms = msWithStatus("RECRUITING");
        lenient().doNothing().when(msRepository).acquireGoldFeaturedLock();
        when(msRepository.selectForUpdate(1L)).thenReturn(ms);
        when(msRepository.selectCount(any())).thenReturn(2L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.setGoldFeatured(1L));
        assertEquals(ErrorCode.MS_GOLD_LIMIT.getCode(), ex.getCode());
    }

    // ==================== helpers ====================

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
}
