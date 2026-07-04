package com.microcourse.plugin.interactive;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.entity.Course;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.dto.SlidePageVO;
import com.microcourse.plugin.interactive.dto.SlideVO;
import com.microcourse.plugin.interactive.entity.CourseSlide;
import com.microcourse.plugin.interactive.entity.SlidePage;
import com.microcourse.plugin.interactive.mapper.CourseSlideMapper;
import com.microcourse.plugin.interactive.mapper.SlidePageMapper;
import com.microcourse.plugin.interactive.service.impl.SlideRenderService;
import com.microcourse.plugin.interactive.service.impl.SlideServiceImpl;
import com.microcourse.repository.CourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SlideServiceTest {

    private CourseSlideMapper courseSlideMapper;
    private SlidePageMapper slidePageMapper;
    private CourseRepository courseRepository;
    private SlideRenderService slideRenderService;
    private SlideServiceImpl slideService;

    @BeforeEach
    void setUp() {
        courseSlideMapper = mock(CourseSlideMapper.class);
        slidePageMapper = mock(SlidePageMapper.class);
        courseRepository = mock(CourseRepository.class);
        slideRenderService = mock(SlideRenderService.class);
        slideService = new SlideServiceImpl(courseSlideMapper, slidePageMapper, courseRepository, slideRenderService);
        ReflectionTestUtils.setField(slideService, "storagePath", "/tmp/slides-test");
        ReflectionTestUtils.setField(slideService, "pageImageWidth", 1920);
        ReflectionTestUtils.setField(slideService, "thumbnailWidth", 320);
    }

    @Nested
    @DisplayName("查询课件")
    class GetByCourse {
        @Test
        @DisplayName("存在时返回 SlideVO")
        void getByCourseId_Found() {
            CourseSlide slide = new CourseSlide();
            slide.setId(1L);
            slide.setCourseId(1L);
            slide.setFileName("test.pptx");
            slide.setTotalPages(10);
            slide.setStatus(2);
            when(courseSlideMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(slide);

            SlideVO vo = slideService.getByCourseId(1L);
            assertNotNull(vo);
            assertEquals(1L, vo.getId());
            assertEquals(10, vo.getTotalPages());
            assertEquals(2, vo.getStatus());
        }

        @Test
        @DisplayName("不存在时返回 null")
        void getByCourseId_NotFound() {
            when(courseSlideMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            assertNull(slideService.getByCourseId(999L));
        }
    }

    @Nested
    @DisplayName("获取页面列表")
    class GetPages {
        @Test
        @DisplayName("返回排序后的页面列表")
        void getPages_ReturnsSorted() {
            CourseSlide slide = new CourseSlide();
            slide.setId(1L);
            slide.setCourseId(1L);
            slide.setStatus(2);
            when(courseSlideMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(slide);

            SlidePage p1 = new SlidePage(); p1.setId(1L); p1.setPageNumber(1); p1.setSlideId(1L);
            p1.setCourseId(1L); p1.setNarrationStatus("PENDING");
            SlidePage p2 = new SlidePage(); p2.setId(2L); p2.setPageNumber(2); p2.setSlideId(1L);
            p2.setCourseId(1L); p2.setNarrationStatus("AI_GENERATED");
            when(slidePageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(p1, p2));

            List<SlidePageVO> pages = slideService.getPages(1L, null);
            assertEquals(2, pages.size());
            assertEquals(1, pages.get(0).getPageNumber());
            assertEquals("PENDING", pages.get(0).getNarrationStatus());
        }

        @Test
        @DisplayName("无课件时返回空列表")
        void getPages_NoSlide() {
            when(courseSlideMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            assertTrue(slideService.getPages(1L, null).isEmpty());
        }
    }

    @Nested
    @DisplayName("获取单页详情")
    class GetPage {
        @Test
        @DisplayName("存在时返回 SlidePageVO")
        void getPage_Found() {
            SlidePage page = new SlidePage();
            page.setId(1L); page.setCourseId(1L); page.setPageNumber(1);
            page.setNarrationScript("测试讲述稿"); page.setNarrationStatus("AI_GENERATED");
            when(slidePageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(page);

            SlidePageVO vo = slideService.getPage(1L, 1);
            assertNotNull(vo);
            assertEquals("测试讲述稿", vo.getNarrationScript());
        }

        @Test
        @DisplayName("不存在时抛 SLIDE_PAGE_NOT_FOUND")
        void getPage_NotFound() {
            when(slidePageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            BusinessException e = assertThrows(BusinessException.class,
                    () -> slideService.getPage(1L, 999));
            assertEquals(ErrorCode.SLIDE_PAGE_NOT_FOUND.getCode(), e.getCode());
        }
    }

    @Nested
    @DisplayName("更新页面")
    class UpdatePage {
        @Test
        @DisplayName("课件不存在抛 COURSE_NOT_FOUND")
        void updatePage_CourseNotFound() {
            SlidePage existingPage = new SlidePage();
            existingPage.setId(1L); existingPage.setCourseId(1L); existingPage.setPageNumber(1);
            when(slidePageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingPage);
            when(courseRepository.selectById(1L)).thenReturn(null);
            BusinessException e = assertThrows(BusinessException.class,
                    () -> slideService.updatePage(1L, 1, Map.of("narrationScript", "test")));
            assertEquals(ErrorCode.COURSE_NOT_FOUND.getCode(), e.getCode());
        }

        @Test
        @DisplayName("页面不存在抛异常")
        void updatePage_NotFound() {
            when(courseRepository.selectById(1L)).thenReturn(new Course());
            when(slidePageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            BusinessException e = assertThrows(BusinessException.class,
                    () -> slideService.updatePage(1L, 999, Map.of("narrationScript", "test")));
            assertEquals(ErrorCode.SLIDE_PAGE_NOT_FOUND.getCode(), e.getCode());
        }
    }

    @Nested
    @DisplayName("排序页面")
    class Reorder {
        @Test
        @DisplayName("重新排序调用 updateById")
        void reorderPages_CallsUpdate() {
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            SecurityContextHolder.getContext().setAuthentication(auth);
            try {
                Course course = new Course();
                course.setId(1L);
                course.setTeacherId(1L);
                when(courseRepository.selectById(1L)).thenReturn(course);

                SlidePage page = new SlidePage();
                page.setId(1L); page.setCourseId(1L); page.setPageNumber(1);
                when(slidePageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(page);

                slideService.reorderPages(1L, List.of(Map.of("pageNumber", 1, "newPageNumber", 2)));
                verify(slidePageMapper, times(2)).updateById(any());
            } finally {
                SecurityContextHolder.clearContext();
            }
        }

        @Test
        @DisplayName("排序忽略不变项")
        void reorderPages_SkipsUnchanged() {
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            SecurityContextHolder.getContext().setAuthentication(auth);
            try {
                when(courseRepository.selectById(1L)).thenReturn(new Course());
                slideService.reorderPages(1L, List.of(Map.of("pageNumber", 1, "newPageNumber", 1)));
                verify(slidePageMapper, never()).updateById(any());
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }
}
