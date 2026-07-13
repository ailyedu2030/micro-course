package com.microcourse.plugin.interactive;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.entity.Course;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.dto.SlidePageVO;
import com.microcourse.plugin.interactive.dto.SlideUploadResponse;
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

import org.springframework.mock.web.MockMultipartFile;
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
        slideService = new SlideServiceImpl(courseSlideMapper, slidePageMapper, courseRepository, null, null, slideRenderService);
        ReflectionTestUtils.setField(slideService, "storagePath", "/tmp/slides-test");
        ReflectionTestUtils.setField(slideService, "maxHtmlSize", 5L * 1024 * 1024);
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
            when(courseSlideMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(slide));

            SlideVO vo = slideService.getByCourseId(1L);
            assertNotNull(vo);
            assertEquals(1L, vo.getId());
            assertEquals(10, vo.getTotalPages());
            assertEquals(2, vo.getStatus());
        }

        @Test
        @DisplayName("不存在时返回 null")
        void getByCourseId_NotFound() {
            when(courseSlideMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
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
            when(slidePageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(page));

            SlidePageVO vo = slideService.getPage(1L, 1);
            assertNotNull(vo);
            assertEquals("测试讲述稿", vo.getNarrationScript());
        }

        @Test
        @DisplayName("不存在时抛 SLIDE_PAGE_NOT_FOUND")
        void getPage_NotFound() {
            when(slidePageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
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
                when(slidePageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(page));

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

    @Nested
    @DisplayName("HTML 上传")
    class HtmlUpload {

        private void setupAdminContext() {
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        @Test
        @DisplayName("HTML 文件上传成功")
        void uploadHtmlFile_Success() {
            setupAdminContext();
            try {
                Course course = new Course();
                course.setId(1L);
                course.setTeacherId(1L);
                when(courseRepository.selectById(1L)).thenReturn(course);
                when(courseSlideMapper.insert(any(CourseSlide.class))).thenAnswer(inv -> {
                    CourseSlide s = inv.getArgument(0);
                    s.setId(43L);
                    return 1;
                });
                when(slidePageMapper.insert(any(SlidePage.class))).thenReturn(1);

                MockMultipartFile file = new MockMultipartFile(
                        "file", "lesson.html", "text/html", "<p>File Upload Test</p>".getBytes());

                SlideUploadResponse resp = slideService.uploadHtmlFile(1L, file, null, null);

                assertNotNull(resp);
                assertEquals(1, resp.getTotalPages());
                assertEquals(2, resp.getStatus());
                assertEquals(43L, resp.getSlideId().longValue());
                verify(courseSlideMapper).insert(any(CourseSlide.class));
                verify(slidePageMapper).insert(any(SlidePage.class));
            } finally {
                SecurityContextHolder.clearContext();
            }
        }

        @Test
        @DisplayName("HTML 文件超过 5MB 抛异常")
        void uploadHtmlFile_TooLarge() {
            setupAdminContext();
            try {
                Course course = new Course();
                course.setId(1L);
                course.setTeacherId(1L);
                when(courseRepository.selectById(1L)).thenReturn(course);

                MockMultipartFile bigFile = new MockMultipartFile(
                        "file", "big.html", "text/html", new byte[5 * 1024 * 1024 + 1]);

                BusinessException e = assertThrows(BusinessException.class,
                        () -> slideService.uploadHtmlFile(1L, bigFile, null, null));
                assertEquals(ErrorCode.HTML_TOO_LARGE.getCode(), e.getCode());
            } finally {
                SecurityContextHolder.clearContext();
            }
        }

        @Test
        @DisplayName("HTML UPSERT — 同一 chapterId 重复上传，复用 slide_id 并删旧页")
        void uploadHtmlFile_Upsert() {
            setupAdminContext();
            try {
                Course course = new Course();
                course.setId(1L);
                course.setTeacherId(1L);
                when(courseRepository.selectById(1L)).thenReturn(course);

                CourseSlide existing = new CourseSlide();
                existing.setId(43L);
                existing.setCourseId(1L);
                existing.setChapterId(10L);
                existing.setFileName("old.html");
                existing.setFileUrl("html:inline");
                existing.setStatus(2);
                // UPSERT 查询应返回 existing
                when(courseSlideMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
                when(courseSlideMapper.updateById(any(CourseSlide.class))).thenReturn(1);
                when(slidePageMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(3);

                MockMultipartFile newFile = new MockMultipartFile(
                        "file", "new.html", "text/html", "<p>New content</p>".getBytes());

                SlideUploadResponse resp = slideService.uploadHtmlFile(1L, newFile, 10L, 10L);

                // 1. 复用 slide_id=43（不重新 insert）
                assertEquals(43L, resp.getSlideId().longValue());
                // 2. 旧 slide_pages 被删（UPSERT 的关键）
                verify(slidePageMapper).delete(any(LambdaQueryWrapper.class));
                // 3. 新的 slide 走 update 而非 insert
                verify(courseSlideMapper, never()).insert(any(CourseSlide.class));
                verify(courseSlideMapper).updateById(any(CourseSlide.class));
                // 4. 新 page 插入
                verify(slidePageMapper).insert(any(SlidePage.class));
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }
}
