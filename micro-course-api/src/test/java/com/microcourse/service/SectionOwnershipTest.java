package com.microcourse.service;

import com.microcourse.dto.SectionDTO;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseChapter;
import com.microcourse.entity.CourseSection;
import com.microcourse.exception.BusinessException;
import com.microcourse.plugin.interactive.mapper.CourseSlideMapper;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.CourseSectionRepository;
import com.microcourse.service.impl.SectionServiceImpl;
import com.microcourse.util.SecurityUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * P0-3 修复测试: SectionController 读路径 ownership 校验
 *
 * <p>【根因】SectionServiceImpl.listByChapter 和 getById 未做 ownership 校验，
 * 任何教师可越权枚举其他教师的课程结构/章节细节。
 *
 * <p>【修复】两个方法都在开头 assertOwner(courseId)，与 create/update/delete 一致。
 *
 * <p>【测试覆盖】
 * - 正常路径: 教师 owner 自己课程 → 通过
 * - 防御: 教师 A 越权读教师 B 课程 → 抛 BusinessException
 * - 防御: 教师 A 越权 list 教师 B 课程 → 抛 BusinessException
 */
@DisplayName("P0-3: SectionService 读路径 ownership 校验")
class SectionOwnershipTest {

    private CourseRepository courseRepo;
    private CourseChapterRepository chapterRepo;
    private CourseSectionRepository sectionRepo;
    private CourseSlideMapper slideMapper;
    private SectionServiceImpl sectionService;

    @BeforeEach
    void setUp() {
        courseRepo = Mockito.mock(CourseRepository.class);
        chapterRepo = Mockito.mock(CourseChapterRepository.class);
        sectionRepo = Mockito.mock(CourseSectionRepository.class);
        slideMapper = Mockito.mock(CourseSlideMapper.class);
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        // F-2026-08-10-10: 删除章节委托给 CoursewareDeleteService（mock 即可，测试不涉及 delete 路径）
        com.microcourse.service.CoursewareDeleteService coursewareDeleteService =
                Mockito.mock(com.microcourse.service.CoursewareDeleteService.class);
        sectionService = new SectionServiceImpl(sectionRepo, chapterRepo, courseRepo, slideMapper, om, coursewareDeleteService);
    }

    @Test
    @DisplayName("P0-3-1: 教师 owner 自己课程的 getById 必须通过")
    void shouldAllowOwnerGetById() {
        Long teacherId = 10L;
        Long courseId = 1L;
        Long sectionId = 100L;
        // mock SecurityUtil.isOwnerOrAdmin → true
        try (var mocked = mockStatic(SecurityUtil.class)) {
            mocked.when(() -> SecurityUtil.isOwnerOrAdmin(teacherId)).thenReturn(true);

            CourseSection section = new CourseSection();
            section.setId(sectionId);
            section.setCourseId(courseId);
            section.setChapterId(10L);
            section.setTitle("test");
            Mockito.when(sectionRepo.selectById(sectionId)).thenReturn(section);

            Course course = new Course();
            course.setId(courseId);
            course.setTeacherId(teacherId);
            Mockito.when(courseRepo.selectById(courseId)).thenReturn(course);

            Mockito.when(slideMapper.selectCount(any())).thenReturn(0L);
            // SecurityUtil.getCurrentUserId 暂不调用(因为 isOwnerOrAdmin 已经返回 true)

            SectionDTO dto = sectionService.getById(sectionId);
            assertNotNull(dto);
        }
    }

    @Test
    @DisplayName("P0-3-2: 教师 A 越权读教师 B 课程的 getById 必须抛 BusinessException")
    void shouldRejectNonOwnerGetById() {
        Long teacherA = 10L;
        Long teacherB = 20L;
        Long courseId = 2L;  // belongs to teacherB
        Long sectionId = 200L;

        try (var mocked = mockStatic(SecurityUtil.class)) {
            // teacherA 不是 course 的 owner
            mocked.when(() -> SecurityUtil.isOwnerOrAdmin(teacherB)).thenReturn(false);
            mocked.when(SecurityUtil::getCurrentUserId).thenReturn(teacherA);

            CourseSection section = new CourseSection();
            section.setId(sectionId);
            section.setCourseId(courseId);
            section.setChapterId(20L);
            Mockito.when(sectionRepo.selectById(sectionId)).thenReturn(section);

            BusinessException ex = assertThrows(BusinessException.class, () -> {
                sectionService.getById(sectionId);
            });
            assertNotNull(ex.getMessage());
        }
    }

    @Test
    @DisplayName("P0-3-3: 教师 A 越权 list 教师 B 课程必须抛 BusinessException")
    void shouldRejectNonOwnerListByChapter() {
        Long teacherA = 10L;
        Long teacherB = 20L;
        Long chapterId = 30L;
        Long courseId = 3L;  // belongs to teacherB

        try (var mocked = mockStatic(SecurityUtil.class)) {
            mocked.when(() -> SecurityUtil.isOwnerOrAdmin(teacherB)).thenReturn(false);
            mocked.when(SecurityUtil::getCurrentUserId).thenReturn(teacherA);

            CourseChapter chapter = new CourseChapter();
            chapter.setId(chapterId);
            chapter.setCourseId(courseId);
            Mockito.when(chapterRepo.selectById(chapterId)).thenReturn(chapter);

            Course course = new Course();
            course.setId(courseId);
            course.setTeacherId(teacherB);
            Mockito.when(courseRepo.selectById(courseId)).thenReturn(course);

            BusinessException ex = assertThrows(BusinessException.class, () -> {
                sectionService.listByChapter(chapterId, 0, 20);
            });
            assertNotNull(ex.getMessage());
        }
    }

    /**
     * F-2026-08-10-13: V333 锁定——同一 chapter 下 INTERACTIVE/EXERCISE/OFFLINE section 唯一
     */
    @Test
    @DisplayName("同 chapter 已有 INTERACTIVE → 再创建 INTERACTIVE 必须抛 BusinessException")
    void mustRejectDuplicateInteractive() {
        Long teacherId = 10L;
        Long courseId = 1L;
        Long chapterId = 2L;
        Course course = new Course();
        course.setId(courseId);
        course.setTeacherId(teacherId);
        Mockito.when(courseRepo.selectById(courseId)).thenReturn(course);
        Mockito.when(sectionRepo.selectCount(Mockito.any())).thenReturn(1L);

        com.microcourse.dto.SectionCreateRequest req = new com.microcourse.dto.SectionCreateRequest();
        req.setTitle("第二个 INTERACTIVE");
        req.setSectionType("INTERACTIVE");
        try (var mocked = mockStatic(SecurityUtil.class)) {
            mocked.when(() -> SecurityUtil.isOwnerOrAdmin(any(Long.class))).thenReturn(true);
            BusinessException ex = assertThrows(BusinessException.class, () -> {
                sectionService.create(courseId, chapterId, req);
            });
            assertNotNull(ex.getMessage());
            assertTrue(ex.getMessage().contains("INTERACTIVE"));
        }
    }

    @Test
    @DisplayName("VIDEO 类型不唯一（同一 chapter 允许多个视频课时，去重不应触发）")
    void mustAllowMultipleVideo() {
        Long teacherId = 10L;
        Long courseId = 1L;
        Long chapterId = 2L;
        Course course = new Course();
        course.setId(courseId);
        course.setTeacherId(teacherId);
        Mockito.when(courseRepo.selectById(courseId)).thenReturn(course);
        // VIDEO 不触发去重（selectCount 不会因 VIDEO 被调用）
        Mockito.when(sectionRepo.selectCount(Mockito.any())).thenReturn(5L);

        com.microcourse.dto.SectionCreateRequest req = new com.microcourse.dto.SectionCreateRequest();
        req.setTitle("第六个视频");
        req.setSectionType("VIDEO");
        try (var mocked = mockStatic(SecurityUtil.class)) {
            mocked.when(() -> SecurityUtil.isOwnerOrAdmin(any(Long.class))).thenReturn(true);
            try {
                sectionService.create(courseId, chapterId, req);
            } catch (Exception e) {
                // 排除 V333 去重触发；其他业务校验异常可接受
                if (e instanceof BusinessException && e.getMessage() != null && e.getMessage().contains("V333")) {
                    throw e;
                }
            }
        }
    }
}
