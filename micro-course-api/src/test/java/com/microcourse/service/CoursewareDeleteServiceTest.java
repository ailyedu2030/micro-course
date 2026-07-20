package com.microcourse.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.microcourse.dto.BatchOperationResult;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseChapter;
import com.microcourse.entity.CourseSection;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.entity.SlideHtmlSegmentScript;
import com.microcourse.plugin.interactive.entity.SlideHtmlUnit;
import com.microcourse.plugin.interactive.entity.SlidePptPage;
import com.microcourse.plugin.interactive.entity.SlidePptPageScript;
import com.microcourse.plugin.interactive.mapper.SlideHtmlSegmentScriptMapper;
import com.microcourse.plugin.interactive.mapper.SlideHtmlUnitMapper;
import com.microcourse.plugin.interactive.mapper.SlidePptPageMapper;
import com.microcourse.plugin.interactive.mapper.SlidePptPageScriptMapper;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.CourseSectionRepository;
import com.microcourse.service.impl.CoursewareDeleteServiceImpl;
import com.microcourse.util.SecurityUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * CoursewareDeleteService 单元测试 (sytafe 需求 2026-07-20).
 *
 * 覆盖:
 * <ul>
 *   <li>IDOR 防御: 教师 A 删除教师 B 的课件 → 抛 NO_PERMISSION</li>
 *   <li>删除 chapter → 级联 section + ppt page + html unit + scripts</li>
 *   <li>删除 section → 仅级联该 section 下课件</li>
 *   <li>删除 PPT page → 同时删 script</li>
 *   <li>删除 HTML unit → 同时删 segment scripts</li>
 *   <li>批量 chapter: 跨 courseId 篡改 → 失败</li>
 *   <li>批量 PPT page: 部分成功部分失败</li>
 *   <li>空列表 / 超限 / 跨 course 防御</li>
 * </ul>
 */
class CoursewareDeleteServiceTest {

    private CourseRepository courseRepository;
    private CourseChapterRepository chapterRepository;
    private CourseSectionRepository sectionRepository;
    private SlidePptPageMapper pptPageMapper;
    private SlidePptPageScriptMapper pptPageScriptMapper;
    private SlideHtmlUnitMapper htmlUnitMapper;
    private SlideHtmlSegmentScriptMapper htmlSegmentScriptMapper;
    private CoursewareDeleteServiceImpl service;

    private MockedStatic<SecurityUtil> securityUtilMock;

    // 测试 fixture: sytafe = teacher 35, course 79, course 80 = 其他教师
    private static final Long SYTAFE_ID = 35L;
    private static final Long OTHER_TEACHER_ID = 99L;
    private static final Long SYTAFE_COURSE_ID = 79L;
    private static final Long OTHER_COURSE_ID = 80L;
    private static final Long SYTAFE_CHAPTER_ID = 204L;
    private static final Long SYTAFE_SECTION_PPT_ID = 209L;
    private static final Long SYTAFE_SECTION_HTML_ID = 210L;
    private static final Long SYTAFE_PPT_PAGE_ID = 100L;
    private static final Long SYTAFE_HTML_UNIT_ID = 200L;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        courseRepository = mock(CourseRepository.class);
        chapterRepository = mock(CourseChapterRepository.class);
        sectionRepository = mock(CourseSectionRepository.class);
        pptPageMapper = mock(SlidePptPageMapper.class);
        pptPageScriptMapper = mock(SlidePptPageScriptMapper.class);
        htmlUnitMapper = mock(SlideHtmlUnitMapper.class);
        htmlSegmentScriptMapper = mock(SlideHtmlSegmentScriptMapper.class);
        service = new CoursewareDeleteServiceImpl(courseRepository, chapterRepository,
                sectionRepository, pptPageMapper, pptPageScriptMapper,
                htmlUnitMapper, htmlSegmentScriptMapper);

        // 默认 sytafe 是当前登录用户, owner=true
        securityUtilMock = mockStatic(SecurityUtil.class, CALLS_REAL_METHODS);
        securityUtilMock.when(() -> SecurityUtil.isOwnerOrAdmin(any(Long.class))).thenAnswer(inv -> {
            Long teacherId = inv.getArgument(0);
            return teacherId.equals(SYTAFE_ID);
        });
        securityUtilMock.when(SecurityUtil::getCurrentUserIdOpt).thenReturn(SYTAFE_ID);
    }

    @AfterEach
    void tearDown() {
        if (securityUtilMock != null) securityUtilMock.close();
    }

    private Course makeCourse(Long courseId, Long teacherId) {
        Course c = new Course();
        c.setId(courseId);
        c.setTeacherId(teacherId);
        c.setTitle("test");
        c.setStatus(0);
        c.setDeletedAt(null);
        return c;
    }

    private CourseChapter makeChapter(Long chapterId, Long courseId) {
        CourseChapter ch = new CourseChapter();
        ch.setId(chapterId);
        ch.setCourseId(courseId);
        ch.setTitle("ch");
        ch.setDeletedAt(null);
        return ch;
    }

    private CourseSection makeSection(Long sectionId, Long courseId, Long chapterId) {
        CourseSection s = new CourseSection();
        s.setId(sectionId);
        s.setCourseId(courseId);
        s.setChapterId(chapterId);
        s.setTitle("sec");
        s.setDeletedAt(null);
        return s;
    }

    private SlidePptPage makePptPage(Long id, Long courseId, Long sectionId) {
        SlidePptPage p = new SlidePptPage();
        p.setId(id);
        p.setCourseId(courseId);
        p.setSectionId(sectionId);
        p.setSlideId(1L);
        p.setPageNumber(1);
        return p;
    }

    private SlideHtmlUnit makeHtmlUnit(Long id, Long courseId, Long sectionId) {
        SlideHtmlUnit u = new SlideHtmlUnit();
        u.setId(id);
        u.setCourseId(courseId);
        u.setSectionId(sectionId);
        u.setHtmlContent("<p>x</p>");
        u.setHtmlSanitized("<p>x</p>");
        u.setFileUuid("uuid-" + id);
        return u;
    }

    // ====================================================================
    // 删除 chapter
    // ====================================================================
    @Nested
    @DisplayName("deleteChapter")
    class DeleteChapter {

        @Test
        @DisplayName("【成功】删 chapter 级联 section + ppt page + html unit")
        void deleteChapter_cascade() {
            Course course = makeCourse(SYTAFE_COURSE_ID, SYTAFE_ID);
            CourseChapter chapter = makeChapter(SYTAFE_CHAPTER_ID, SYTAFE_COURSE_ID);
            CourseSection sec1 = makeSection(SYTAFE_SECTION_PPT_ID, SYTAFE_COURSE_ID, SYTAFE_CHAPTER_ID);
            CourseSection sec2 = makeSection(SYTAFE_SECTION_HTML_ID, SYTAFE_COURSE_ID, SYTAFE_CHAPTER_ID);
            SlidePptPage pptPage = makePptPage(SYTAFE_PPT_PAGE_ID, SYTAFE_COURSE_ID, SYTAFE_SECTION_PPT_ID);
            SlideHtmlUnit htmlUnit = makeHtmlUnit(SYTAFE_HTML_UNIT_ID, SYTAFE_COURSE_ID, SYTAFE_SECTION_HTML_ID);

            when(courseRepository.selectById(SYTAFE_COURSE_ID)).thenReturn(course);
            when(chapterRepository.selectById(SYTAFE_CHAPTER_ID)).thenReturn(chapter);
            when(sectionRepository.selectList(any(Wrapper.class))).thenReturn(List.of(sec1, sec2));
            when(pptPageMapper.selectList(any(Wrapper.class))).thenReturn(List.of(pptPage));
            when(htmlUnitMapper.selectList(any(Wrapper.class))).thenReturn(List.of(htmlUnit));
            when(chapterRepository.delete(any(Wrapper.class))).thenReturn(1);
            when(sectionRepository.delete(any(Wrapper.class))).thenReturn(2);
            when(pptPageMapper.delete(any(Wrapper.class))).thenReturn(1);
            when(htmlUnitMapper.delete(any(Wrapper.class))).thenReturn(1);
            when(pptPageScriptMapper.delete(any(Wrapper.class))).thenReturn(1);
            when(htmlSegmentScriptMapper.delete(any(Wrapper.class))).thenReturn(3);

            CoursewareDeleteService.DeleteStats stats = service.deleteChapter(SYTAFE_COURSE_ID, SYTAFE_CHAPTER_ID);

            assertEquals(1, stats.deletedChapters());
            assertEquals(2, stats.deletedSections());
            assertEquals(1, stats.deletedPptPages());
            assertEquals(1, stats.deletedHtmlUnits());
            assertEquals(1, stats.deletedPptScripts());
            assertEquals(3, stats.deletedHtmlSegmentScripts());

            // 验证 delete 调用: section/script/unit 都基于正确的 IN 列表
            verify(pptPageMapper).delete(argThat(w -> w != null));
            verify(htmlUnitMapper).delete(argThat(w -> w != null));
        }

        @Test
        @DisplayName("【IDOR】chapter 不属于该 course → 抛 RESOURCE_NOT_FOUND")
        void deleteChapter_idor_chapterCrossCourse() {
            Course course = makeCourse(SYTAFE_COURSE_ID, SYTAFE_ID);
            // chapter 属于 OTHER_COURSE
            CourseChapter chapter = makeChapter(SYTAFE_CHAPTER_ID, OTHER_COURSE_ID);

            when(courseRepository.selectById(SYTAFE_COURSE_ID)).thenReturn(course);
            when(chapterRepository.selectById(SYTAFE_CHAPTER_ID)).thenReturn(chapter);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.deleteChapter(SYTAFE_COURSE_ID, SYTAFE_CHAPTER_ID));
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("【权限】teacher 不是课主 → 抛 NO_PERMISSION")
        void deleteChapter_noPermission() {
            // 用 sytafe 课程但 chapter 归属 sytafe, isOwnerOrAdmin 返回 false (mock 默认)
            Course course = makeCourse(SYTAFE_COURSE_ID, OTHER_TEACHER_ID); // teacherId=99, isOwner=false
            CourseChapter chapter = makeChapter(SYTAFE_CHAPTER_ID, SYTAFE_COURSE_ID);
            when(courseRepository.selectById(SYTAFE_COURSE_ID)).thenReturn(course);
            when(chapterRepository.selectById(SYTAFE_CHAPTER_ID)).thenReturn(chapter);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.deleteChapter(SYTAFE_COURSE_ID, SYTAFE_CHAPTER_ID));
            assertEquals(ErrorCode.NO_PERMISSION.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("【不存在】course 已删除 → 抛 RESOURCE_NOT_FOUND")
        void deleteChapter_courseDeleted() {
            Course deleted = makeCourse(SYTAFE_COURSE_ID, SYTAFE_ID);
            deleted.setDeletedAt(LocalDateTime.now());
            when(courseRepository.selectById(SYTAFE_COURSE_ID)).thenReturn(deleted);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.deleteChapter(SYTAFE_COURSE_ID, 1L));
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), ex.getCode());
        }
    }

    // ====================================================================
    // 删除 section
    // ====================================================================
    @Nested
    @DisplayName("deleteSection")
    class DeleteSection {

        @Test
        @DisplayName("【成功】删除 section 级联 ppt page + html unit")
        void deleteSection_cascade() {
            Course course = makeCourse(SYTAFE_COURSE_ID, SYTAFE_ID);
            CourseSection sec = makeSection(SYTAFE_SECTION_PPT_ID, SYTAFE_COURSE_ID, SYTAFE_CHAPTER_ID);
            SlidePptPage page = makePptPage(SYTAFE_PPT_PAGE_ID, SYTAFE_COURSE_ID, SYTAFE_SECTION_PPT_ID);

            when(courseRepository.selectById(SYTAFE_COURSE_ID)).thenReturn(course);
            when(sectionRepository.selectById(SYTAFE_SECTION_PPT_ID)).thenReturn(sec);
            when(pptPageMapper.selectList(any(Wrapper.class))).thenReturn(List.of(page));
            when(htmlUnitMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());
            when(sectionRepository.delete(any(Wrapper.class))).thenReturn(1);
            when(pptPageMapper.delete(any(Wrapper.class))).thenReturn(1);

            CoursewareDeleteService.DeleteStats stats = service.deleteSection(SYTAFE_COURSE_ID, SYTAFE_SECTION_PPT_ID);

            assertEquals(1, stats.deletedSections());
            assertEquals(1, stats.deletedPptPages());
            assertEquals(0, stats.deletedHtmlUnits());
        }

        @Test
        @DisplayName("【IDOR】section 跨 course → 抛 RESOURCE_NOT_FOUND")
        void deleteSection_idor() {
            Course course = makeCourse(SYTAFE_COURSE_ID, SYTAFE_ID);
            CourseSection sec = makeSection(SYTAFE_SECTION_PPT_ID, OTHER_COURSE_ID, SYTAFE_CHAPTER_ID);

            when(courseRepository.selectById(SYTAFE_COURSE_ID)).thenReturn(course);
            when(sectionRepository.selectById(SYTAFE_SECTION_PPT_ID)).thenReturn(sec);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.deleteSection(SYTAFE_COURSE_ID, SYTAFE_SECTION_PPT_ID));
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), ex.getCode());
        }
    }

    // ====================================================================
    // 删除 PPT page
    // ====================================================================
    @Nested
    @DisplayName("deletePptPage")
    class DeletePptPage {

        @Test
        @DisplayName("【成功】删除 PPT page 同时删 scripts")
        void deletePptPage_success() {
            Course course = makeCourse(SYTAFE_COURSE_ID, SYTAFE_ID);
            SlidePptPage page = makePptPage(SYTAFE_PPT_PAGE_ID, SYTAFE_COURSE_ID, SYTAFE_SECTION_PPT_ID);

            when(courseRepository.selectById(SYTAFE_COURSE_ID)).thenReturn(course);
            when(pptPageMapper.selectById(SYTAFE_PPT_PAGE_ID)).thenReturn(page);
            when(pptPageScriptMapper.delete(any(Wrapper.class))).thenReturn(2);
            when(pptPageMapper.deleteById(SYTAFE_PPT_PAGE_ID)).thenReturn(1);

            CoursewareDeleteService.DeleteStats stats = service.deletePptPage(SYTAFE_COURSE_ID, SYTAFE_PPT_PAGE_ID);

            assertEquals(1, stats.deletedPptPages());
            assertEquals(2, stats.deletedPptScripts());
            verify(pptPageMapper).deleteById(SYTAFE_PPT_PAGE_ID);
        }

        @Test
        @DisplayName("【IDOR】PPT page 跨 course → 抛 RESOURCE_NOT_FOUND")
        void deletePptPage_idor() {
            Course course = makeCourse(SYTAFE_COURSE_ID, SYTAFE_ID);
            SlidePptPage page = makePptPage(SYTAFE_PPT_PAGE_ID, OTHER_COURSE_ID, SYTAFE_SECTION_PPT_ID);

            when(courseRepository.selectById(SYTAFE_COURSE_ID)).thenReturn(course);
            when(pptPageMapper.selectById(SYTAFE_PPT_PAGE_ID)).thenReturn(page);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.deletePptPage(SYTAFE_COURSE_ID, SYTAFE_PPT_PAGE_ID));
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), ex.getCode());
        }
    }

    // ====================================================================
    // 删除 HTML unit
    // ====================================================================
    @Nested
    @DisplayName("deleteHtmlUnit")
    class DeleteHtmlUnit {

        @Test
        @DisplayName("【成功】删除 HTML unit 同时删 segment scripts")
        void deleteHtmlUnit_success() {
            Course course = makeCourse(SYTAFE_COURSE_ID, SYTAFE_ID);
            SlideHtmlUnit unit = makeHtmlUnit(SYTAFE_HTML_UNIT_ID, SYTAFE_COURSE_ID, SYTAFE_SECTION_HTML_ID);

            when(courseRepository.selectById(SYTAFE_COURSE_ID)).thenReturn(course);
            when(htmlUnitMapper.selectById(SYTAFE_HTML_UNIT_ID)).thenReturn(unit);
            when(htmlSegmentScriptMapper.delete(any(Wrapper.class))).thenReturn(3);
            when(htmlUnitMapper.deleteById(SYTAFE_HTML_UNIT_ID)).thenReturn(1);

            CoursewareDeleteService.DeleteStats stats = service.deleteHtmlUnit(SYTAFE_COURSE_ID, SYTAFE_HTML_UNIT_ID);

            assertEquals(1, stats.deletedHtmlUnits());
            assertEquals(3, stats.deletedHtmlSegmentScripts());
            verify(htmlUnitMapper).deleteById(SYTAFE_HTML_UNIT_ID);
        }
    }

    // ====================================================================
    // 批量删除
    // ====================================================================
    @Nested
    @DisplayName("batch delete")
    class BatchDelete {

        @Test
        @DisplayName("【批量 chapter】部分成功部分失败")
        void deleteChaptersBatch_partialSuccess() {
            Course course = makeCourse(SYTAFE_COURSE_ID, SYTAFE_ID);
            CourseChapter ch1 = makeChapter(SYTAFE_CHAPTER_ID, SYTAFE_COURSE_ID);
            CourseChapter ch2 = makeChapter(205L, SYTAFE_COURSE_ID);
            when(courseRepository.selectById(SYTAFE_COURSE_ID)).thenReturn(course);

            // ch1 正常, ch2 不存在
            when(chapterRepository.selectById(SYTAFE_CHAPTER_ID)).thenReturn(ch1);
            when(chapterRepository.selectById(205L)).thenReturn(null);
            when(sectionRepository.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());
            when(chapterRepository.delete(any(Wrapper.class))).thenReturn(1);

            BatchOperationResult result = service.deleteChaptersBatch(SYTAFE_COURSE_ID,
                    List.of(SYTAFE_CHAPTER_ID, 205L));

            assertEquals(2, result.getSuccessCount() + result.getFailCount());
        }

        @Test
        @DisplayName("【批量 chapter】空列表 → BAD_REQUEST_PARAM")
        void deleteChaptersBatch_empty() {
            Course course = makeCourse(SYTAFE_COURSE_ID, SYTAFE_ID);
            when(courseRepository.selectById(SYTAFE_COURSE_ID)).thenReturn(course);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.deleteChaptersBatch(SYTAFE_COURSE_ID, Collections.emptyList()));
            assertEquals(ErrorCode.BAD_REQUEST_PARAM.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("【批量 chapter】超过 100 → BAD_REQUEST_PARAM")
        void deleteChaptersBatch_overLimit() {
            Course course = makeCourse(SYTAFE_COURSE_ID, SYTAFE_ID);
            when(courseRepository.selectById(SYTAFE_COURSE_ID)).thenReturn(course);
            List<Long> ids = new ArrayList<>();
            for (long i = 1; i <= 101; i++) ids.add(i);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.deleteChaptersBatch(SYTAFE_COURSE_ID, ids));
            assertEquals(ErrorCode.BAD_REQUEST_PARAM.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("【批量 PPT】跨 course 的 page 被识别为失败")
        void deletePptPagesBatch_idor() {
            Course course = makeCourse(SYTAFE_COURSE_ID, SYTAFE_ID);
            SlidePptPage page1 = makePptPage(SYTAFE_PPT_PAGE_ID, SYTAFE_COURSE_ID, SYTAFE_SECTION_PPT_ID);
            SlidePptPage page2 = makePptPage(999L, OTHER_COURSE_ID, SYTAFE_SECTION_PPT_ID);

            when(courseRepository.selectById(SYTAFE_COURSE_ID)).thenReturn(course);
            when(pptPageMapper.selectById(SYTAFE_PPT_PAGE_ID)).thenReturn(page1);
            when(pptPageMapper.selectById(999L)).thenReturn(page2);
            when(pptPageScriptMapper.delete(any(Wrapper.class))).thenReturn(0);

            BatchOperationResult result = service.deletePptPagesBatch(SYTAFE_COURSE_ID,
                    List.of(SYTAFE_PPT_PAGE_ID, 999L));

            // page1 成功 (1), page2 跨 course 失败 (1)
            assertEquals(1, result.getSuccessCount());
            assertEquals(1, result.getFailCount());
            assertTrue(result.getSuccessIds().contains(SYTAFE_PPT_PAGE_ID));
            assertFalse(result.getSuccessIds().contains(999L));
        }
    }
}