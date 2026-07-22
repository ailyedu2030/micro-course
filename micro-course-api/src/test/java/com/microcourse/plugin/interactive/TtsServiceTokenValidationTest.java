package com.microcourse.plugin.interactive;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.plugin.interactive.entity.SlidePage;
import com.microcourse.plugin.interactive.mapper.SlidePageMapper;
import com.microcourse.plugin.interactive.service.impl.TtsServiceImpl;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.CourseSectionRepository;
import com.microcourse.repository.EnrollmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TtsServiceImpl.validateAudioToken 测试 (P1-C 修复 2026-07-19)
 *
 * <p>背景: 单页 HTML_DIRECT 场景下,reload 工具生成 15 个 token URL
 * (pageNumber=1..15),但 SlidePage 表只有 pageNumber=1 一条记录。
 * 旧逻辑严格按 pageNumber 查 → pageNumber=2..15 全部 403。
 * 新逻辑增加 sectionId 级 fallback。
 */
@SuppressWarnings("unchecked")
class TtsServiceTokenValidationTest {

    private SlidePageMapper slidePageMapper;
    private CourseRepository courseRepository;
    private CourseSectionRepository courseSectionRepository;
    private EnrollmentRepository enrollmentRepository;
    private TtsServiceImpl ttsService;

    @BeforeEach
    void setUp() {
        slidePageMapper = mock(SlidePageMapper.class);
        courseRepository = mock(CourseRepository.class);
        courseSectionRepository = mock(CourseSectionRepository.class);
        enrollmentRepository = mock(EnrollmentRepository.class);

        ttsService = new TtsServiceImpl(
                slidePageMapper, courseRepository,
                enrollmentRepository, courseSectionRepository,
                mock(org.springframework.transaction.support.TransactionTemplate.class),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(java.util.concurrent.ExecutorService.class));
        ReflectionTestUtils.setField(ttsService, "storagePath", "/tmp/test-storage");
    }

    @Test
    @DisplayName("空 token 返回 false")
    void nullToken_ReturnsFalse() {
        assertFalse(ttsService.validateAudioToken(52L, 1, 650L, null));
        assertFalse(ttsService.validateAudioToken(52L, 1, 650L, ""));
        assertFalse(ttsService.validateAudioToken(52L, 1, 650L, "   "));
    }

    @Test
    @DisplayName("空 sectionId 返回 false")
    void nullSectionId_ReturnsFalse() {
        assertFalse(ttsService.validateAudioToken(52L, 1, null, "abc123"));
    }

    @Test
    @DisplayName("精确匹配 pageNumber=1 命中 - 旧逻辑路径")
    void exactMatch_PageNumberOne_ReturnsTrue() {
        SlidePage page = new SlidePage();
        page.setPageNumber(1);
        page.setNarrationAudioUrl("/api/courses/52/slides/pages/1/audio?sectionId=650&v=2&token=abc123");
        when(slidePageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(page);

        assertTrue(ttsService.validateAudioToken(52L, 1, 650L, "abc123"));
        verify(slidePageMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("pageNumber=15 无精确记录,fallback 到 sectionId 级校验")
    void fallback_SectionIdLevel_ReturnsTrue() {
        // 单页 HTML_DIRECT 场景: pageNumber=15 在 SlidePage 表里查不到
        when(slidePageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // 但 sectionId=650 下 page=1 持有 token
        SlidePage page1 = new SlidePage();
        page1.setPageNumber(1);
        page1.setNarrationAudioUrl("/api/courses/52/slides/pages/1/audio?sectionId=650&v=2&token=abc123");
        when(slidePageMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(page1));

        assertTrue(ttsService.validateAudioToken(52L, 15, 650L, "abc123"));
    }

    @Test
    @DisplayName("无精确匹配 + sectionId 下无 token → 返回 false (403)")
    void noMatch_AnyLevel_ReturnsFalse() {
        when(slidePageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        SlidePage page1 = new SlidePage();
        page1.setPageNumber(1);
        page1.setNarrationAudioUrl("/api/courses/52/slides/pages/1/audio?sectionId=650&v=2&token=other_token");
        when(slidePageMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(page1));

        assertFalse(ttsService.validateAudioToken(52L, 15, 650L, "abc123"));
    }

    @Test
    @DisplayName("精确匹配但 token 不一致 → fallback 也找不到 → false")
    void exactMatchButTokenMismatch_ReturnsFalse() {
        SlidePage page = new SlidePage();
        page.setPageNumber(1);
        page.setNarrationAudioUrl("/api/courses/52/slides/pages/1/audio?sectionId=650&v=2&token=other_token");
        when(slidePageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(page);
        when(slidePageMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(page));

        assertFalse(ttsService.validateAudioToken(52L, 1, 650L, "abc123"));
    }

    @Test
    @DisplayName("sectionId 下多个 page,任一含 token 即通过")
    void multiPageSection_AnyMatch_ReturnsTrue() {
        when(slidePageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        SlidePage page1 = new SlidePage();
        page1.setPageNumber(1);
        page1.setNarrationAudioUrl("/api/courses/52/slides/pages/1/audio?sectionId=650&v=2&token=old_token");
        SlidePage page2 = new SlidePage();
        page2.setPageNumber(2);
        page2.setNarrationAudioUrl("/api/courses/52/slides/pages/2/audio?sectionId=650&v=2&token=new_token");
        when(slidePageMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(page1, page2));

        assertTrue(ttsService.validateAudioToken(52L, 5, 650L, "new_token"));
    }
}