package com.microcourse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseChapter;
import com.microcourse.entity.CourseSection;
import com.microcourse.plugin.interactive.entity.CourseSlide;
import com.microcourse.plugin.interactive.entity.SlidePage;
import com.microcourse.plugin.interactive.entity.SlidePptPage;
import com.microcourse.plugin.interactive.mapper.CourseSlideMapper;
import com.microcourse.plugin.interactive.mapper.SlidePageMapper;
import com.microcourse.plugin.interactive.service.SlideService;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.CourseSectionRepository;
import com.microcourse.service.impl.CoursewareDeleteServiceImpl;
import com.microcourse.util.SecurityUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F-2026-08-10-09: 教师删除章节时级联清理 v1 course_slides + slide_pages + 物理文件
 *
 * 之前盲区：CoursewareDeleteServiceImpl.deleteSectionsAndCourseware 只清理 v2 表（PPT pages/HTML units）
 * 和 section，遗漏 v1 course_slides（无 FK CASCADE）→ 删除章节后孤儿课件残留（统计错误+文件残留）
 */
class CoursewareDeleteCascadeTest {

    private static final Long COURSE_ID = 100L;
    private static final Long CHAPTER_ID = 200L;
    private static final Long SECTION_ID = 300L;
    private static final Long TEACHER_ID = 999L;
    private static final Long SLIDE_ID_1 = 401L;
    private static final Long SLIDE_ID_2 = 402L;
    private static final Long SLIDE_PAGE_ID_1 = 501L;

    private CourseRepository courseRepository;
    private CourseChapterRepository chapterRepository;
    private CourseSectionRepository sectionRepository;
    private CourseSlideMapper courseSlideMapper;
    private SlidePageMapper slidePageMapper;
    private SlideService slideService;
    private CoursewareDeleteServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisPlusTestHelper.initTableInfo();
        Configuration cfg = new Configuration();
        cfg.setMapUnderscoreToCamelCase(true);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        TableInfoHelper.initTableInfo(assistant, CourseSlide.class);
        TableInfoHelper.initTableInfo(assistant, SlidePage.class);
        TableInfoHelper.initTableInfo(assistant, CourseSection.class);
        TableInfoHelper.initTableInfo(assistant, CourseChapter.class);
    }

    void setUp() {
        courseRepository = mock(CourseRepository.class);
        chapterRepository = mock(CourseChapterRepository.class);
        sectionRepository = mock(CourseSectionRepository.class);
        courseSlideMapper = mock(CourseSlideMapper.class);
        slidePageMapper = mock(SlidePageMapper.class);
        slideService = mock(SlideService.class);
        // v2 表 mappers 也需要 mock（即使返回空集合，避免 NullPointer）
        com.microcourse.plugin.interactive.mapper.SlidePptPageMapper pptPageMapper =
                mock(com.microcourse.plugin.interactive.mapper.SlidePptPageMapper.class);
        com.microcourse.plugin.interactive.mapper.SlidePptPageScriptMapper pptPageScriptMapper =
                mock(com.microcourse.plugin.interactive.mapper.SlidePptPageScriptMapper.class);
        com.microcourse.plugin.interactive.mapper.SlideHtmlUnitMapper htmlUnitMapper =
                mock(com.microcourse.plugin.interactive.mapper.SlideHtmlUnitMapper.class);
        com.microcourse.plugin.interactive.mapper.SlideHtmlSegmentScriptMapper htmlSegmentScriptMapper =
                mock(com.microcourse.plugin.interactive.mapper.SlideHtmlSegmentScriptMapper.class);
        // 默认 v2 表返回空（测试只关注 v1 课件清理）
        when(pptPageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(htmlUnitMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        service = new CoursewareDeleteServiceImpl(courseRepository, chapterRepository,
                sectionRepository, pptPageMapper, pptPageScriptMapper,
                htmlUnitMapper, htmlSegmentScriptMapper,
                courseSlideMapper, slidePageMapper, slideService);
    }

    @Test
    @DisplayName("【核心】删除章节 → 同时清理 v1 course_slides + slide_pages（无 FK CASCADE）")
    void deleteChapter_mustCascadeV1SlidesAndPages() {
        setUp();

        // 准备：1 个 chapter + 1 个 section + 2 个 course_slides + 1 个 slide_page
        Course course = makeCourse();
        CourseChapter chapter = makeChapter();
        CourseSection section = makeSection();
        CourseSlide slide1 = slideWithCourse(SLIDE_ID_1, SECTION_ID, COURSE_ID);
        CourseSlide slide2 = slideWithCourse(SLIDE_ID_2, SECTION_ID, COURSE_ID);
        SlidePage slidePage = slidePageWithSlide(SLIDE_PAGE_ID_1, SLIDE_ID_1);

        when(courseRepository.selectById(COURSE_ID)).thenReturn(course);
        when(chapterRepository.selectById(CHAPTER_ID)).thenReturn(chapter);
        when(sectionRepository.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(section));
        when(courseSlideMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(slide1, slide2));
        when(slidePageMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(slidePage));

        try (MockedStatic<SecurityUtil> secMock = mockStatic(SecurityUtil.class)) {
            secMock.when(() -> SecurityUtil.isOwnerOrAdmin(anyLong())).thenReturn(true);

            service.deleteChapter(COURSE_ID, CHAPTER_ID);

            // 验证 v1 course_slides 被物理删除（按 section_ids 过滤）
            ArgumentCaptor<LambdaQueryWrapper> courseSlideCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(courseSlideMapper, atLeastOnce()).selectList(courseSlideCaptor.capture());
            verify(courseSlideMapper, times(1)).delete(any(LambdaQueryWrapper.class));

            // 验证 v1 slide_pages 被物理删除（按 slide_ids 过滤，先于 course_slides 删除避免 FK 违反）
            verify(slidePageMapper, times(1)).delete(any(LambdaQueryWrapper.class));

            // 验证物理文件清理被触发（afterCommit 钩子）
            verify(slideService, atLeastOnce()).cleanupSlideFiles(eq(COURSE_ID), eq(SLIDE_ID_1));
            verify(slideService, atLeastOnce()).cleanupSlideFiles(eq(COURSE_ID), eq(SLIDE_ID_2));
        }
    }

    @Test
    @DisplayName("【边界】空 section_ids → 不触发任何清理（短路）")
    void deleteChapter_emptySections() {
        setUp();
        Course course = makeCourse();
        CourseChapter chapter = makeChapter();
        when(courseRepository.selectById(COURSE_ID)).thenReturn(course);
        when(chapterRepository.selectById(CHAPTER_ID)).thenReturn(chapter);
        when(sectionRepository.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        try (MockedStatic<SecurityUtil> secMock = mockStatic(SecurityUtil.class)) {
            secMock.when(() -> SecurityUtil.isOwnerOrAdmin(anyLong())).thenReturn(true);
            service.deleteChapter(COURSE_ID, CHAPTER_ID);

            verify(courseSlideMapper, never()).delete(any(LambdaQueryWrapper.class));
            verify(slidePageMapper, never()).delete(any(LambdaQueryWrapper.class));
            verify(slideService, never()).cleanupSlideFiles(any(), any());
        }
    }

    @Test
    @DisplayName("【边界】courseId 为 null 的 course_slides → 物理文件清理使用 fallback null")
    void deleteChapter_slidesWithoutCourseId_useFallbackForCleanup() {
        setUp();
        Course course = makeCourse();
        CourseChapter chapter = makeChapter();
        CourseSection section = makeSection();
        // slide.courseId=null（异常数据），仍触发 cleanup 但用 null fallback
        CourseSlide orphanSlide = slideWithCourse(SLIDE_ID_1, SECTION_ID, null);

        when(courseRepository.selectById(COURSE_ID)).thenReturn(course);
        when(chapterRepository.selectById(CHAPTER_ID)).thenReturn(chapter);
        when(sectionRepository.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(section));
        when(courseSlideMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(orphanSlide));
        when(slidePageMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        try (MockedStatic<SecurityUtil> secMock = mockStatic(SecurityUtil.class)) {
            secMock.when(() -> SecurityUtil.isOwnerOrAdmin(anyLong())).thenReturn(true);
            service.deleteChapter(COURSE_ID, CHAPTER_ID);

            verify(courseSlideMapper, times(1)).delete(any(LambdaQueryWrapper.class));
            // F-2026-08-10-11 修复：孤儿 slide 的 courseId=null → 兜底用当前 courseId（避免物理文件路径拼接 null）
            verify(slideService, atLeastOnce()).cleanupSlideFiles(eq(COURSE_ID), eq(SLIDE_ID_1));
        }
    }

    @Test
    @DisplayName("【新增】删除章节 → 同时清理章节级挂载课件（chapterId 关联 + section_id IS NULL）")
    void deleteChapter_mustCascadeChapterLevelSlides() {
        setUp();

        // 准备：1 chapter + 0 sections + 1 章节级挂载课件（section_id=NULL）
        Course course = makeCourse();
        CourseChapter chapter = makeChapter();
        CourseSlide chapterLevelSlide = chapterLevelSlideInChapter(SLIDE_ID_1, COURSE_ID, CHAPTER_ID);

        when(courseRepository.selectById(COURSE_ID)).thenReturn(course);
        when(chapterRepository.selectById(CHAPTER_ID)).thenReturn(chapter);
        when(sectionRepository.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        // 章节级课件查询：按 chapterId + section_id IS NULL（mock 返回章节级课件）
        // 课时级课件查询：按 sectionId 集合（mock 返回空——无课时级课件）
        when(courseSlideMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenAnswer(inv -> Collections.singletonList(chapterLevelSlide));  // 章节级课件（任意 wrapper）
        when(slidePageMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        try (MockedStatic<SecurityUtil> secMock = mockStatic(SecurityUtil.class)) {
            secMock.when(() -> SecurityUtil.isOwnerOrAdmin(anyLong())).thenReturn(true);
            service.deleteChapter(COURSE_ID, CHAPTER_ID);

            // 验证章节级课件被物理删除（service 内部按 chapterId 收集 → 合并到 slideIds → delete）
            verify(courseSlideMapper, atLeastOnce()).delete(any(LambdaQueryWrapper.class));
            // 验证物理文件清理使用真实 courseId（章节级课件 courseId 已知 = 100L）
            verify(slideService, atLeastOnce()).cleanupSlideFiles(eq(COURSE_ID), eq(SLIDE_ID_1));
        }
    }

    // ---- helpers ----
    private Course makeCourse() {
        Course c = new Course();
        c.setId(COURSE_ID);
        c.setTeacherId(TEACHER_ID);
        return c;
    }
    private CourseChapter makeChapter() {
        CourseChapter ch = new CourseChapter();
        ch.setId(CHAPTER_ID);
        ch.setCourseId(COURSE_ID);
        return ch;
    }
    private CourseSection makeSection() {
        CourseSection s = new CourseSection();
        s.setId(SECTION_ID);
        s.setChapterId(CHAPTER_ID);
        s.setCourseId(COURSE_ID);
        return s;
    }
    private CourseSlide slideWithCourse(Long id, Long sectionId, Long courseId) {
        CourseSlide s = new CourseSlide();
        s.setId(id);
        s.setSectionId(sectionId);
        s.setCourseId(courseId);
        return s;
    }
    private CourseSlide chapterLevelSlideInChapter(Long id, Long courseId, Long chapterId) {
        CourseSlide s = new CourseSlide();
        s.setId(id);
        s.setSectionId(null);  // 章节级挂载（V333 PPT 锚点场景）
        s.setChapterId(chapterId);
        s.setCourseId(courseId);
        return s;
    }
    private SlidePage slidePageWithSlide(Long id, Long slideId) {
        SlidePage p = new SlidePage();
        p.setId(id);
        p.setSlideId(slideId);
        return p;
    }
}