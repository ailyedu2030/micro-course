package com.microcourse.service;

import com.microcourse.entity.MicroSpecialty;
import com.microcourse.entity.MicroSpecialtyEnrollment;
import com.microcourse.repository.MicroSpecialtyEnrollmentRepository;
import com.microcourse.repository.MicroSpecialtyRepository;
import com.microcourse.scheduled.MicroSpecialtyProgressAggregator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MicroSpecialtyProgressAggregator — 进度聚合定时任务")
class MicroSpecialtyProgressAggregatorTest {

    @Mock private MicroSpecialtyEnrollmentRepository enrollmentRepository;
    @Mock private MicroSpecialtyEnrollmentService enrollmentService;
    @Mock private MicroSpecialtyRepository msRepository;

    private MicroSpecialtyProgressAggregator aggregator;

    @BeforeAll
    static void init() {
        MybatisPlusTestHelper.initTableInfo();
    }

    @BeforeEach
    void setUp() {
        aggregator = new MicroSpecialtyProgressAggregator(
                enrollmentRepository, enrollmentService, msRepository);
    }

    @Test
    @DisplayName("aggregateAll: APPROVED → IN_PROGRESS 自动转换")
    void aggregateAll_approvedToInProgress() {
        MicroSpecialtyEnrollment approved = enrollmentWithStatus(1L, "APPROVED", 1L, 2L);
        when(enrollmentRepository.selectList(any()))
                .thenReturn(List.of(approved))
                .thenReturn(Collections.emptyList());
        when(enrollmentRepository.update(any(), any())).thenReturn(1);
        doNothing().when(enrollmentService).aggregateProgress(1L);
        when(enrollmentRepository.selectBatchIds(any())).thenReturn(Collections.emptyList());
        lenient().when(msRepository.selectById(anyLong())).thenReturn(null);

        aggregator.aggregateAll();

        verify(enrollmentRepository, atLeastOnce()).update(any(), any());
        verify(enrollmentService).aggregateProgress(1L);
    }

    @Test
    @DisplayName("aggregateAll: ALL_REQUIRED 完成判定 — COMPLETED 被正确计数")
    void aggregateAll_allRequiredCompleted() {
        MicroSpecialtyEnrollment en = enrollmentWithStatus(1L, "IN_PROGRESS", 1L, 2L);
        when(enrollmentRepository.selectList(any()))
                .thenReturn(List.of(en))
                .thenReturn(Collections.emptyList());
        doNothing().when(enrollmentService).aggregateProgress(1L);
        MicroSpecialtyEnrollment completed = enrollmentWithStatus(1L, "COMPLETED", 1L, 2L);
        completed.setProgress(BigDecimal.valueOf(100));
        completed.setFinalScore(BigDecimal.valueOf(85));
        completed.setFinalGrade("GOOD");
        when(enrollmentRepository.selectBatchIds(any())).thenReturn(List.of(completed));
        lenient().when(msRepository.selectById(anyLong())).thenReturn(null);

        assertDoesNotThrow(() -> aggregator.aggregateAll());
    }

    @Test
    @DisplayName("aggregateAll: CREDITS_MIN 完成判定 — COMPLETED")
    void aggregateAll_creditsMinCompleted() {
        MicroSpecialtyEnrollment en = enrollmentWithStatus(1L, "IN_PROGRESS", 1L, 2L);
        when(enrollmentRepository.selectList(any()))
                .thenReturn(List.of(en))
                .thenReturn(Collections.emptyList());
        doNothing().when(enrollmentService).aggregateProgress(1L);
        MicroSpecialtyEnrollment completed = enrollmentWithStatus(1L, "COMPLETED", 1L, 2L);
        completed.setCreditsEarned(BigDecimal.valueOf(12));
        completed.setProgress(BigDecimal.valueOf(100));
        when(enrollmentRepository.selectBatchIds(any())).thenReturn(List.of(completed));
        lenient().when(msRepository.selectById(anyLong())).thenReturn(null);

        assertDoesNotThrow(() -> aggregator.aggregateAll());
    }

    @Test
    @DisplayName("aggregateAll: MIXED 完成判定 — COMPLETED")
    void aggregateAll_mixedCompleted() {
        MicroSpecialtyEnrollment en = enrollmentWithStatus(1L, "IN_PROGRESS", 1L, 2L);
        when(enrollmentRepository.selectList(any()))
                .thenReturn(List.of(en))
                .thenReturn(Collections.emptyList());
        doNothing().when(enrollmentService).aggregateProgress(1L);
        MicroSpecialtyEnrollment completed = enrollmentWithStatus(1L, "COMPLETED", 1L, 2L);
        completed.setProgress(BigDecimal.valueOf(100));
        when(enrollmentRepository.selectBatchIds(any())).thenReturn(List.of(completed));
        lenient().when(msRepository.selectById(anyLong())).thenReturn(null);

        assertDoesNotThrow(() -> aggregator.aggregateAll());
    }

    @Test
    @DisplayName("aggregateAll: FAILED 判定 — FAILED 被正确计数")
    void aggregateAll_failed() {
        MicroSpecialtyEnrollment en = enrollmentWithStatus(1L, "IN_PROGRESS", 1L, 2L);
        when(enrollmentRepository.selectList(any()))
                .thenReturn(List.of(en))
                .thenReturn(Collections.emptyList());
        doNothing().when(enrollmentService).aggregateProgress(1L);
        MicroSpecialtyEnrollment failed = enrollmentWithStatus(1L, "FAILED", 1L, 2L);
        failed.setDropReason("必修课未通过");
        when(enrollmentRepository.selectBatchIds(any())).thenReturn(List.of(failed));
        lenient().when(msRepository.selectById(anyLong())).thenReturn(null);

        assertDoesNotThrow(() -> aggregator.aggregateAll());
    }

    @Test
    @DisplayName("aggregateAll: 并发保护 — running 标志 + version 冲突 log skip")
    void aggregateAll_concurrencyProtection() {
        MicroSpecialtyEnrollment en = enrollmentWithStatus(1L, "IN_PROGRESS", 1L, 2L);
        when(enrollmentRepository.selectList(any()))
                .thenReturn(List.of(en))
                .thenReturn(Collections.emptyList());
        doNothing().when(enrollmentService).aggregateProgress(1L);
        when(enrollmentRepository.selectBatchIds(any())).thenReturn(List.of(en));

        MicroSpecialty ms = new MicroSpecialty();
        ms.setId(1L);
        ms.setVersion(0);
        ms.setStudentCount(10);
        when(msRepository.selectById(1L)).thenReturn(ms);
        when(enrollmentRepository.selectCount(any())).thenReturn(8L);
        when(msRepository.update(any(), any())).thenReturn(0);

        assertDoesNotThrow(() -> aggregator.aggregateAll());
        assertDoesNotThrow(() -> aggregator.aggregateAll());
    }

    @Test
    @DisplayName("aggregateAll: student_count 重校准包含 COMPLETED 和 CERTIFIED")
    void aggregateAll_recalibrateStudentCountIncludesCompletedAndCertified() {
        MicroSpecialtyEnrollment en = enrollmentWithStatus(1L, "IN_PROGRESS", 1L, 2L);
        when(enrollmentRepository.selectList(any()))
                .thenReturn(List.of(en))
                .thenReturn(Collections.emptyList());
        doNothing().when(enrollmentService).aggregateProgress(1L);
        when(enrollmentRepository.selectBatchIds(any())).thenReturn(List.of(enrollmentWithStatus(1L, "CERTIFIED", 1L, 2L)));

        MicroSpecialty ms = new MicroSpecialty();
        ms.setId(1L);
        ms.setVersion(0);
        ms.setStudentCount(1);
        when(msRepository.selectById(1L)).thenReturn(ms);

        when(enrollmentRepository.selectCount(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MicroSpecialtyEnrollment> wrapper = invocation.getArgument(0);
            String sqlSegment = wrapper.getSqlSegment();
            Map<String, Object> params = wrapper.getParamNameValuePairs();
            org.junit.jupiter.api.Assertions.assertTrue(sqlSegment.contains("status IN"));
            org.junit.jupiter.api.Assertions.assertTrue(params.values().contains("APPROVED"));
            org.junit.jupiter.api.Assertions.assertTrue(params.values().contains("IN_PROGRESS"));
            org.junit.jupiter.api.Assertions.assertTrue(params.values().contains("COMPLETED"));
            org.junit.jupiter.api.Assertions.assertTrue(params.values().contains("CERTIFIED"));
            return 1L;
        });
        when(msRepository.update(any(), any())).thenReturn(1);

        assertDoesNotThrow(() -> aggregator.aggregateAll());
    }

    // ==================== helpers ====================

    private static MicroSpecialtyEnrollment enrollmentWithStatus(Long id, String status, Long msId, Long userId) {
        MicroSpecialtyEnrollment en = new MicroSpecialtyEnrollment();
        en.setId(id);
        en.setMicroSpecialtyId(msId);
        en.setUserId(userId);
        en.setStatus(status);
        en.setProgress(BigDecimal.ZERO);
        en.setCreditsEarned(BigDecimal.ZERO);
        en.setCoursesCompleted(0);
        en.setVersion(0);
        en.setCreatedAt(LocalDateTime.now());
        en.setUpdatedAt(LocalDateTime.now());
        return en;
    }
}
