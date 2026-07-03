package com.microcourse.service;

import com.microcourse.repository.CourseReviewRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.MicroSpecialtyCourseRepository;
import com.microcourse.repository.MicroSpecialtyRepository;
import com.microcourse.service.impl.MicroSpecialtyQualityScoreServiceImpl;
import com.microcourse.service.impl.MicroSpecialtyQualityScoreServiceImpl.Cache;
import com.microcourse.entity.MicroSpecialty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 14 — G1 修复：微专业质量分服务单元测试（纯 Mockito，无 DB 依赖）
 *
 * <p>覆盖：
 * <ul>
 *   <li>公式正确性：enrollmentRate × 0.5 + completionRate × 0.3 + (avgRating/5) × 0.2</li>
 *   <li>边界：maxStudents=0 视为 1.0；studentCount=0 视为 0.0</li>
 *   <li>边界：无课程时 completionRate=0；无评论时 avgRating=0</li>
 *   <li>缓存：命中后跳过 DB</li>
 *   <li>批量：calculateBatch 走批量 SQL，不 N+1</li>
 * </ul>
 *
 * @author Phase14-Development-Team
 * @since 2026-06-23
 */
@DisplayName("Phase 14 G1 MicroSpecialtyQualityScoreService")
class MicroSpecialtyQualityScoreServiceTest {

    private MicroSpecialtyRepository msRepo;
    private MicroSpecialtyCourseRepository msCourseRepo;
    private EnrollmentRepository enrollRepo;
    private CourseReviewRepository reviewRepo;
    private Cache cache;
    private MicroSpecialtyQualityScoreServiceImpl service;

    @BeforeEach
    void setUp() {
        msRepo = mock(MicroSpecialtyRepository.class);
        msCourseRepo = mock(MicroSpecialtyCourseRepository.class);
        enrollRepo = mock(EnrollmentRepository.class);
        reviewRepo = mock(CourseReviewRepository.class);
        cache = mock(Cache.class);
        // 默认缓存 miss
        when(cache.get(anyString())).thenReturn(null);
        service = new MicroSpecialtyQualityScoreServiceImpl(
                msRepo, msCourseRepo, enrollRepo, reviewRepo, cache);
    }

    @Test
    @DisplayName("公式正确性：综合分 = 0.5×选课率 + 0.3×完成率 + 0.2×(评分/5)")
    void formula_fullCase() {
        MicroSpecialty ms = new MicroSpecialty();
        ms.setId(1L);
        ms.setStudentCount(50);
        ms.setMaxStudents(100);
        when(msRepo.selectBatchIds(anyList())).thenReturn(Collections.singletonList(ms));

        Map<String, Object> link = new HashMap<>();
        link.put("micro_specialty_id", 1L);
        link.put("course_id", 100L);
        when(msCourseRepo.selectCourseIdsByMsIds(anyList())).thenReturn(Collections.singletonList(link));

        List<Map<String, Object>> completed = new ArrayList<>();
        Map<String, Object> c1 = new HashMap<>();
        c1.put("course_id", 100L);
        c1.put("cnt", 8L);
        completed.add(c1);
        when(enrollRepo.countCompletedByCourseIds(anyList(), anyString())).thenReturn(completed);

        List<Map<String, Object>> inProgressOrCompleted = new ArrayList<>();
        Map<String, Object> c2 = new HashMap<>();
        c2.put("course_id", 100L);
        c2.put("cnt", 10L);
        inProgressOrCompleted.add(c2);
        when(enrollRepo.countInProgressOrCompletedByCourseIds(anyList(), anyString(), anyString(), anyString())).thenReturn(inProgressOrCompleted);

        List<Map<String, Object>> ratings = new ArrayList<>();
        Map<String, Object> r1 = new HashMap<>();
        r1.put("course_id", 100L);
        r1.put("avg_rating", new BigDecimal("4.0"));
        ratings.add(r1);
        when(reviewRepo.avgRatingByCourseIds(anyList())).thenReturn(ratings);

        BigDecimal score = service.calculate(1L);
        assertNotNull(score);
        // enrollmentRate=0.5, completionRate=0.8, avgRating=4.0/5=0.8
        // score = 0.5*0.5 + 0.8*0.3 + 0.8*0.2 = 0.25 + 0.24 + 0.16 = 0.65
        assertEquals(0, new BigDecimal("0.6500").compareTo(score),
                "质量分应为 0.6500，实际：" + score);
    }

    @Test
    @DisplayName("边界：maxStudents=0（不限）→ enrollmentRate=1.0")
    void boundary_maxStudents_zero() {
        MicroSpecialty ms = new MicroSpecialty();
        ms.setId(2L);
        ms.setStudentCount(0);
        ms.setMaxStudents(0);
        when(msRepo.selectBatchIds(anyList())).thenReturn(Collections.singletonList(ms));
        when(msCourseRepo.selectCourseIdsByMsIds(anyList())).thenReturn(Collections.emptyList());
        when(enrollRepo.countCompletedByCourseIds(anyList(), anyString())).thenReturn(Collections.emptyList());
        when(enrollRepo.countInProgressOrCompletedByCourseIds(anyList(), anyString(), anyString(), anyString())).thenReturn(Collections.emptyList());
        when(reviewRepo.avgRatingByCourseIds(anyList())).thenReturn(Collections.emptyList());

        BigDecimal score = service.calculate(2L);
        // enrollmentRate=1.0, completionRate=0.0, avgRating=0
        // = 0.5*1.0 + 0.3*0 + 0.2*0 = 0.5
        assertEquals(0, new BigDecimal("0.5000").compareTo(score),
                "maxStudents=0 时质量分应为 0.5，实际：" + score);
    }

    @Test
    @DisplayName("边界：studentCount > maxStudents → enrollmentRate 截断到 1.0")
    void boundary_overflow() {
        MicroSpecialty ms = new MicroSpecialty();
        ms.setId(3L);
        ms.setStudentCount(200);
        ms.setMaxStudents(100);
        when(msRepo.selectBatchIds(anyList())).thenReturn(Collections.singletonList(ms));
        when(msCourseRepo.selectCourseIdsByMsIds(anyList())).thenReturn(Collections.emptyList());

        BigDecimal score = service.calculate(3L);
        assertEquals(0, new BigDecimal("0.5000").compareTo(score));
    }

    @Test
    @DisplayName("边界：studentCount=0, maxStudents=100 → enrollmentRate=0 → 质量分=0")
    void boundary_empty() {
        MicroSpecialty ms = new MicroSpecialty();
        ms.setId(4L);
        ms.setStudentCount(0);
        ms.setMaxStudents(100);
        when(msRepo.selectBatchIds(anyList())).thenReturn(Collections.singletonList(ms));
        when(msCourseRepo.selectCourseIdsByMsIds(anyList())).thenReturn(Collections.emptyList());

        BigDecimal score = service.calculate(4L);
        assertEquals(0, BigDecimal.ZERO.compareTo(score));
    }

    @Test
    @DisplayName("缓存命中：跳过 DB 查询")
    void cache_hit() {
        when(cache.get("msq:score:5")).thenReturn("0.7500");
        BigDecimal score = service.calculate(5L);
        assertEquals(0, new BigDecimal("0.7500").compareTo(score));
        verify(msRepo, never()).selectBatchIds(anyList());
    }

    @Test
    @DisplayName("缓存写入：DB 计算后写入缓存（1 小时 TTL）")
    void cache_write() {
        MicroSpecialty ms = new MicroSpecialty();
        ms.setId(7L);
        ms.setStudentCount(10);
        ms.setMaxStudents(20);
        when(msRepo.selectBatchIds(anyList())).thenReturn(Collections.singletonList(ms));
        when(msCourseRepo.selectCourseIdsByMsIds(anyList())).thenReturn(Collections.emptyList());

        service.calculate(7L);
        verify(cache, times(1)).set(eq("msq:score:7"), anyString(), eq(Duration.ofHours(1)));
    }

    @Test
    @DisplayName("批量：calculateBatch 走单次 SQL（无 N+1）")
    void batch_singleQuery() {
        when(cache.get(anyString())).thenReturn(null);

        MicroSpecialty m1 = new MicroSpecialty();
        m1.setId(10L); m1.setStudentCount(10); m1.setMaxStudents(20);
        MicroSpecialty m2 = new MicroSpecialty();
        m2.setId(11L); m2.setStudentCount(5); m2.setMaxStudents(20);
        when(msRepo.selectBatchIds(anyList())).thenReturn(Arrays.asList(m1, m2));
        when(msCourseRepo.selectCourseIdsByMsIds(anyList())).thenReturn(Collections.emptyList());

        Map<Long, BigDecimal> result = service.calculateBatch(Arrays.asList(10L, 11L));
        assertEquals(2, result.size());
        // m1: 0.5*0.5=0.25; m2: 0.5*0.25=0.125
        assertEquals(0, new BigDecimal("0.2500").compareTo(result.get(10L)));
        assertEquals(0, new BigDecimal("0.1250").compareTo(result.get(11L)));
        // 验证只调用 1 次主表批量查询
        verify(msRepo, times(1)).selectBatchIds(anyList());
    }

    @Test
    @DisplayName("evictCache：调用 cache.delete")
    void evictCache() {
        service.evictCache(99L);
        verify(cache, times(1)).delete("msq:score:99");
    }

    @Test
    @DisplayName("空 ID 列表 → 返回空 Map")
    void emptyInput() {
        assertTrue(service.calculateBatch(null).isEmpty());
        assertTrue(service.calculateBatch(Collections.emptyList()).isEmpty());
    }

    @Test
    @DisplayName("null ID → 质量分 0")
    void nullId() {
        assertEquals(0, BigDecimal.ZERO.compareTo(service.calculate(null)));
    }
}
