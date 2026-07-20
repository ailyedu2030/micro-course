package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.microcourse.service.CoursewareDeleteService;
import com.microcourse.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 教师自主删除课件实现.
 *
 * <p>【IDOR 防御 7-19 P0】 所有方法都先校验 course.teacher_id = 当前用户,
 * 防止教师 A 篡改 URL 删除教师 B 的课件.</p>
 *
 * <p>【P0 软删除 2026-07-20】 用户决策保持软删除: deleted_at = now(),
 * 数据可恢复, 通过数据治理清理过期数据.</p>
 */
@Service
public class CoursewareDeleteServiceImpl implements CoursewareDeleteService {

    private static final Logger log = LoggerFactory.getLogger(CoursewareDeleteServiceImpl.class);

    private final CourseRepository courseRepository;
    private final CourseChapterRepository chapterRepository;
    private final CourseSectionRepository sectionRepository;
    private final SlidePptPageMapper pptPageMapper;
    private final SlidePptPageScriptMapper pptPageScriptMapper;
    private final SlideHtmlUnitMapper htmlUnitMapper;
    private final SlideHtmlSegmentScriptMapper htmlSegmentScriptMapper;

    public CoursewareDeleteServiceImpl(CourseRepository courseRepository,
                                       CourseChapterRepository chapterRepository,
                                       CourseSectionRepository sectionRepository,
                                       SlidePptPageMapper pptPageMapper,
                                       SlidePptPageScriptMapper pptPageScriptMapper,
                                       SlideHtmlUnitMapper htmlUnitMapper,
                                       SlideHtmlSegmentScriptMapper htmlSegmentScriptMapper) {
        this.courseRepository = courseRepository;
        this.chapterRepository = chapterRepository;
        this.sectionRepository = sectionRepository;
        this.pptPageMapper = pptPageMapper;
        this.pptPageScriptMapper = pptPageScriptMapper;
        this.htmlUnitMapper = htmlUnitMapper;
        this.htmlSegmentScriptMapper = htmlSegmentScriptMapper;
    }

    // ====================================================================
    // 1. 删除 chapter (级联 section + 课件)
    // ====================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeleteStats deleteChapter(Long courseId, Long chapterId) {
        Course course = loadCourseOrThrow(courseId);
        CourseChapter chapter = loadChapterOrThrow(chapterId);
        // 【P0 IDOR 防御】 chapter 必须属于该 course
        if (!chapter.getCourseId().equals(courseId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "chapter " + chapterId + " 不属于 course " + courseId);
        }
        assertOwnership(course);

        // 收集 chapter 下所有 section
        List<CourseSection> sections = sectionRepository.selectList(
                new LambdaQueryWrapper<CourseSection>()
                        .eq(CourseSection::getChapterId, chapterId));
        List<Long> sectionIds = sections.stream().map(CourseSection::getId).collect(Collectors.toList());

        // 软删 chapter
        int deletedChapters = chapterRepository.delete(
                new LambdaQueryWrapper<CourseChapter>()
                        .eq(CourseChapter::getId, chapterId));

        // 级联软删 section + 课件
        DeleteStats cascade = deleteSectionsAndCourseware(sectionIds);

        log.info("[CoursewareDelete] deleteChapter: courseId={}, chapterId={}, sections={}, deletedChapters={}",
                courseId, chapterId, sectionIds.size(), deletedChapters);

        return new DeleteStats(
                deletedChapters,
                cascade.deletedSections(),
                cascade.deletedPptPages(),
                cascade.deletedHtmlUnits(),
                cascade.deletedPptScripts(),
                cascade.deletedHtmlSegmentScripts());
    }

    // ====================================================================
    // 2. 删除单个 section (级联课件)
    // ====================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeleteStats deleteSection(Long courseId, Long sectionId) {
        Course course = loadCourseOrThrow(courseId);
        CourseSection section = loadSectionOrThrow(sectionId);
        if (!section.getCourseId().equals(courseId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "section " + sectionId + " 不属于 course " + courseId);
        }
        assertOwnership(course);

        DeleteStats cascade = deleteSectionsAndCourseware(Collections.singletonList(sectionId));

        log.info("[CoursewareDelete] deleteSection: courseId={}, sectionId={}, stats={}",
                courseId, sectionId, cascade);
        return new DeleteStats(0, cascade.deletedSections(), cascade.deletedPptPages(),
                cascade.deletedHtmlUnits(), cascade.deletedPptScripts(),
                cascade.deletedHtmlSegmentScripts());
    }

    // ====================================================================
    // 3. 删除单个 PPT page (含 script)
    // ====================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeleteStats deletePptPage(Long courseId, Long pptPageId) {
        Course course = loadCourseOrThrow(courseId);
        SlidePptPage page = pptPageMapper.selectById(pptPageId);
        if (page == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "PPT page 不存在: id=" + pptPageId);
        }
        if (!page.getCourseId().equals(courseId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "PPT page " + pptPageId + " 不属于 course " + courseId);
        }
        assertOwnership(course);

        // 软删 script(s)
        int deletedScripts = pptPageScriptMapper.delete(
                new LambdaQueryWrapper<SlidePptPageScript>()
                        .eq(SlidePptPageScript::getPptPageId, pptPageId));

        // 软删 page (slide_ppt_pages 表无 deleted_at, 用 update + version++ 标记)
        int deletedPages = pptPageMapper.deleteById(pptPageId);

        log.info("[CoursewareDelete] deletePptPage: courseId={}, pptPageId={}, deletedScripts={}",
                courseId, pptPageId, deletedScripts);

        return new DeleteStats(0, 0, deletedPages, 0, deletedScripts, 0);
    }

    // ====================================================================
    // 4. 删除单个 HTML unit (含 segment scripts)
    // ====================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeleteStats deleteHtmlUnit(Long courseId, Long htmlUnitId) {
        Course course = loadCourseOrThrow(courseId);
        SlideHtmlUnit unit = htmlUnitMapper.selectById(htmlUnitId);
        if (unit == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "HTML unit 不存在: id=" + htmlUnitId);
        }
        if (!unit.getCourseId().equals(courseId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "HTML unit " + htmlUnitId + " 不属于 course " + courseId);
        }
        assertOwnership(course);

        // 软删 segment scripts
        int deletedSegments = htmlSegmentScriptMapper.delete(
                new LambdaQueryWrapper<SlideHtmlSegmentScript>()
                        .eq(SlideHtmlSegmentScript::getHtmlUnitId, htmlUnitId));

        int deletedUnits = htmlUnitMapper.deleteById(htmlUnitId);

        log.info("[CoursewareDelete] deleteHtmlUnit: courseId={}, htmlUnitId={}, deletedSegments={}",
                courseId, htmlUnitId, deletedSegments);

        return new DeleteStats(0, 0, 0, deletedUnits, 0, deletedSegments);
    }

    // ====================================================================
    // 5. 批量删除 chapter
    // ====================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchOperationResult deleteChaptersBatch(Long courseId, List<Long> chapterIds) {
        if (chapterIds == null || chapterIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "chapterIds 不能为空");
        }
        if (chapterIds.size() > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "批量最多 100 个 chapter");
        }

        Course course = loadCourseOrThrow(courseId);
        assertOwnership(course);

        List<Long> succeeded = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (Long chapterId : chapterIds) {
            try {
                CourseChapter chapter = chapterRepository.selectById(chapterId);
                if (chapter == null || !chapter.getCourseId().equals(courseId)) {
                    failed.add("chapter " + chapterId + " 不存在或不属于该 course");
                    continue;
                }
                // 单条失败不影响其他, 但用 REQUIRES_NEW 隔离 (这里依赖外层事务回滚)
                deleteChapter(courseId, chapterId);
                succeeded.add(chapterId);
            } catch (Exception e) {
                log.warn("[CoursewareDelete] deleteChaptersBatch fail: chapterId={}", chapterId, e);
                failed.add("chapter " + chapterId + ": " + e.getMessage());
            }
        }
        log.info("[CoursewareDelete] deleteChaptersBatch: courseId={}, total={}, success={}, fail={}",
                courseId, chapterIds.size(), succeeded.size(), failed.size());
        return new BatchOperationResult(chapterIds.size(), succeeded.size(), succeeded, failed);
    }

    // ====================================================================
    // 6. 批量删除 PPT page
    // ====================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchOperationResult deletePptPagesBatch(Long courseId, List<Long> pptPageIds) {
        if (pptPageIds == null || pptPageIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "pptPageIds 不能为空");
        }
        if (pptPageIds.size() > 500) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "批量最多 500 个 PPT page");
        }

        Course course = loadCourseOrThrow(courseId);
        assertOwnership(course);

        List<Long> succeeded = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (Long pptPageId : pptPageIds) {
            try {
                SlidePptPage page = pptPageMapper.selectById(pptPageId);
                if (page == null || !page.getCourseId().equals(courseId)) {
                    failed.add("pptPage " + pptPageId + " 不存在或不属于该 course");
                    continue;
                }
                deletePptPage(courseId, pptPageId);
                succeeded.add(pptPageId);
            } catch (Exception e) {
                log.warn("[CoursewareDelete] deletePptPagesBatch fail: pptPageId={}", pptPageId, e);
                failed.add("pptPage " + pptPageId + ": " + e.getMessage());
            }
        }
        log.info("[CoursewareDelete] deletePptPagesBatch: courseId={}, total={}, success={}, fail={}",
                courseId, pptPageIds.size(), succeeded.size(), failed.size());
        return new BatchOperationResult(pptPageIds.size(), succeeded.size(), succeeded, failed);
    }

    // ====================================================================
    // 内部方法
    // ====================================================================

    /**
     * 删除 section 列表 + 级联课件.
     */
    private DeleteStats deleteSectionsAndCourseware(List<Long> sectionIds) {
        if (sectionIds.isEmpty()) {
            return new DeleteStats(0, 0, 0, 0, 0, 0);
        }
        // 收集 PPT pages
        List<SlidePptPage> pptPages = pptPageMapper.selectList(
                new LambdaQueryWrapper<SlidePptPage>()
                        .in(SlidePptPage::getSectionId, sectionIds));
        List<Long> pptPageIds = pptPages.stream().map(SlidePptPage::getId).collect(Collectors.toList());

        // 收集 HTML units
        List<SlideHtmlUnit> htmlUnits = htmlUnitMapper.selectList(
                new LambdaQueryWrapper<SlideHtmlUnit>()
                        .in(SlideHtmlUnit::getSectionId, sectionIds));
        List<Long> htmlUnitIds = htmlUnits.stream().map(SlideHtmlUnit::getId).collect(Collectors.toList());

        // 删除 PPT scripts (按 page id)
        int deletedPptScripts = pptPageIds.isEmpty() ? 0 : pptPageScriptMapper.delete(
                new LambdaQueryWrapper<SlidePptPageScript>()
                        .in(SlidePptPageScript::getPptPageId, pptPageIds));

        // 删除 HTML segment scripts (按 unit id)
        int deletedHtmlSegScripts = htmlUnitIds.isEmpty() ? 0 : htmlSegmentScriptMapper.delete(
                new LambdaQueryWrapper<SlideHtmlSegmentScript>()
                        .in(SlideHtmlSegmentScript::getHtmlUnitId, htmlUnitIds));

        // 删除 PPT pages
        int deletedPptPages = pptPageIds.isEmpty() ? 0 : pptPageMapper.delete(
                new LambdaQueryWrapper<SlidePptPage>()
                        .in(SlidePptPage::getId, pptPageIds));

        // 删除 HTML units
        int deletedHtmlUnits = htmlUnitIds.isEmpty() ? 0 : htmlUnitMapper.delete(
                new LambdaQueryWrapper<SlideHtmlUnit>()
                        .in(SlideHtmlUnit::getId, htmlUnitIds));

        // 删除 sections (软删)
        int deletedSections = sectionRepository.delete(
                new LambdaQueryWrapper<CourseSection>()
                        .in(CourseSection::getId, sectionIds));

        return new DeleteStats(0, deletedSections, deletedPptPages, deletedHtmlUnits,
                deletedPptScripts, deletedHtmlSegScripts);
    }

    private Course loadCourseOrThrow(Long courseId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null || course.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "course 不存在或已删除: id=" + courseId);
        }
        return course;
    }

    private CourseChapter loadChapterOrThrow(Long chapterId) {
        CourseChapter chapter = chapterRepository.selectById(chapterId);
        if (chapter == null || chapter.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "chapter 不存在或已删除: id=" + chapterId);
        }
        return chapter;
    }

    private CourseSection loadSectionOrThrow(Long sectionId) {
        CourseSection section = sectionRepository.selectById(sectionId);
        if (section == null || section.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "section 不存在或已删除: id=" + sectionId);
        }
        return section;
    }

    /**
     * 【P0 IDOR 防御 7-19】 校验当前用户是课主或 ADMIN.
     */
    private void assertOwnership(Course course) {
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            log.warn("[CoursewareDelete] IDOR block: courseId={}, teacherId={}, currentUser={}",
                    course.getId(), course.getTeacherId(), SecurityUtil.getCurrentUserIdOpt());
            throw new BusinessException(ErrorCode.NO_PERMISSION,
                    "无权限操作该课程, courseId=" + course.getId());
        }
    }
}