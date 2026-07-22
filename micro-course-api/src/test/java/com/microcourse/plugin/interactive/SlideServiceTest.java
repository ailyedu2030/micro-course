package com.microcourse.plugin.interactive;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseChapter;
import com.microcourse.entity.CourseSection;
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
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.CourseSectionRepository;
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

@SuppressWarnings("unchecked")
class SlideServiceTest {

    private CourseSlideMapper courseSlideMapper;
    private SlidePageMapper slidePageMapper;
    private CourseRepository courseRepository;
    private CourseChapterRepository courseChapterRepository;
    private CourseSectionRepository courseSectionRepository;
    private SlideRenderService slideRenderService;
    private SlideServiceImpl slideService;

    @BeforeEach
    void setUp() {
        courseSlideMapper = mock(CourseSlideMapper.class);
        slidePageMapper = mock(SlidePageMapper.class);
        courseRepository = mock(CourseRepository.class);
        courseChapterRepository = mock(CourseChapterRepository.class);
        courseSectionRepository = mock(CourseSectionRepository.class);
        slideRenderService = mock(SlideRenderService.class);
        slideService = new SlideServiceImpl(courseSlideMapper, slidePageMapper, courseRepository, courseChapterRepository, courseSectionRepository, slideRenderService);
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
            setupAdminContext();
            try {
                Course course = new Course();
                course.setId(1L);
                course.setTeacherId(1L);
                when(courseRepository.selectById(1L)).thenReturn(course);
                SlidePage page = new SlidePage();
                page.setId(1L); page.setCourseId(1L); page.setPageNumber(1);
                page.setNarrationScript("测试讲述稿"); page.setNarrationStatus("AI_GENERATED");
                when(slidePageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(page));

                SlidePageVO vo = slideService.getPage(1L, 1);
                assertNotNull(vo);
                assertEquals("测试讲述稿", vo.getNarrationScript());
            } finally {
                SecurityContextHolder.clearContext();
            }
        }

        @Test
        @DisplayName("不存在时抛 SLIDE_PAGE_NOT_FOUND")
        void getPage_NotFound() {
            setupAdminContext();
            try {
                Course course = new Course();
                course.setId(1L);
                course.setTeacherId(1L);
                when(courseRepository.selectById(1L)).thenReturn(course);
                when(slidePageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
                BusinessException e = assertThrows(BusinessException.class,
                        () -> slideService.getPage(1L, 999));
                assertEquals(ErrorCode.SLIDE_PAGE_NOT_FOUND.getCode(), e.getCode());
            } finally {
                SecurityContextHolder.clearContext();
            }
        }

        private void setupAdminContext() {
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            SecurityContextHolder.getContext().setAuthentication(auth);
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
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            SecurityContextHolder.getContext().setAuthentication(auth);
            try {
                Course c = new Course();
                c.setId(888L);
                c.setTeacherId(888L);
                when(courseRepository.selectById(888L)).thenReturn(c);
                when(slidePageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
                BusinessException e = assertThrows(BusinessException.class,
                        () -> slideService.updatePage(888L, 999, Map.of("narrationScript", "test")));
                assertEquals(ErrorCode.SLIDE_PAGE_NOT_FOUND.getCode(), e.getCode());
            } finally {
                SecurityContextHolder.clearContext();
            }
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
        @DisplayName("HTML UPSERT — 同一 chapterId 重复上传,复用 slide_id 且保留 audio 元数据")
        void uploadHtmlFile_Upsert() {
            setupAdminContext();
            try {
                Course course = new Course();
                course.setId(1L);
                course.setTeacherId(1L);
                when(courseRepository.selectById(1L)).thenReturn(course);

                CourseChapter chapter = new CourseChapter();
                chapter.setId(10L);
                chapter.setCourseId(1L);
                when(courseChapterRepository.selectById(10L)).thenReturn(chapter);

                CourseSection section = new CourseSection();
                section.setId(10L);
                section.setCourseId(1L);
                section.setVersion(1);
                when(courseSectionRepository.selectById(10L)).thenReturn(section);
                when(courseSectionRepository.updateById(any(CourseSection.class))).thenReturn(1);

                CourseSlide existing = new CourseSlide();
                existing.setId(43L);
                existing.setCourseId(1L);
                existing.setChapterId(10L);
                existing.setFileName("old.html");
                existing.setFileUrl("html:inline");
                existing.setStatus(2);
                when(courseSlideMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
                when(courseSlideMapper.updateById(any(CourseSlide.class))).thenReturn(1);

                // 模拟 page=1 已存在(且含 audio 元数据)
                SlidePage existingPage = new SlidePage();
                existingPage.setId(100L);
                existingPage.setSlideId(43L);
                existingPage.setCourseId(1L);
                existingPage.setSectionId(10L);
                existingPage.setPageNumber(1);
                existingPage.setContentType("HTML_DIRECT");
                existingPage.setHtmlContent("<p>Old HTML</p>");
                existingPage.setNarrationAudioUrl("/api/courses/1/slides/pages/1/audio?sectionId=10&v=2&token=abc123");
                existingPage.setAudioDuration(120);
                existingPage.setSegmentCount(15);
                existingPage.setNarrationStatus("AUDIO_READY");
                // 注意: 第一次 selectOne 找 CourseSlide, 第二次找 SlidePage
                when(slidePageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingPage);
                when(slidePageMapper.updateById(any(SlidePage.class))).thenReturn(1);

                MockMultipartFile newFile = new MockMultipartFile(
                        "file", "new.html", "text/html", "<p>New content</p>".getBytes());

                SlideUploadResponse resp = slideService.uploadHtmlFile(1L, newFile, 10L, 10L);

                assertEquals(43L, resp.getSlideId().longValue());
                // P1-C 修复验证: 不应 delete 旧 page,不应 insert 新 page
                verify(slidePageMapper, never()).delete(any(LambdaQueryWrapper.class));
                verify(slidePageMapper, never()).insert(any(SlidePage.class));
                verify(courseSlideMapper, never()).insert(any(CourseSlide.class));
                verify(courseSlideMapper).updateById(any(CourseSlide.class));
                verify(slidePageMapper).updateById(any(SlidePage.class));
                // audio 元数据应保留: 在 updateById 的参数上验证
                org.mockito.ArgumentCaptor<SlidePage> pageCaptor =
                        org.mockito.ArgumentCaptor.forClass(SlidePage.class);
                verify(slidePageMapper).updateById(pageCaptor.capture());
                SlidePage updated = pageCaptor.getValue();
                assertEquals("<p>New content</p>", updated.getHtmlContent());
                assertEquals("/api/courses/1/slides/pages/1/audio?sectionId=10&v=2&token=abc123",
                        updated.getNarrationAudioUrl());
                assertEquals(15, updated.getSegmentCount().intValue());
                assertEquals("AUDIO_READY", updated.getNarrationStatus());
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }
}
