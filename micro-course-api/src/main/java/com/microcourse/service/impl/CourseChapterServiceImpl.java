package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.ChapterCreateRequest;
import com.microcourse.dto.ChapterUpdateRequest;
import com.microcourse.dto.ChapterVO;
import com.microcourse.dto.ChapterSortRequest;
import com.microcourse.dto.PageResult;
import com.microcourse.entity.ChapterOfflineSession;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseChapter;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.ChapterOfflineSessionRepository;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.ExerciseChapterRepository;
import com.microcourse.repository.ExerciseRepository;
import com.microcourse.entity.LearningProgress;
import com.microcourse.entity.CourseNote;
import com.microcourse.entity.ExerciseChapter;
import com.microcourse.entity.QuestionChapter;
import com.microcourse.repository.CourseNoteRepository;
import com.microcourse.repository.LearningProgressRepository;
import com.microcourse.repository.QuestionChapterRepository;
import com.microcourse.repository.VideoRepository;
import com.microcourse.service.CourseChapterService;
import com.microcourse.util.SecurityUtil;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class CourseChapterServiceImpl implements CourseChapterService {

    private final CourseChapterRepository chapterRepository;
    private final CourseRepository courseRepository;
    private final VideoRepository videoRepository;
    private final ExerciseRepository exerciseRepository;
    private final ChapterOfflineSessionRepository chapterOfflineSessionRepository;
    private final LearningProgressRepository learningProgressRepository;
    private final CourseNoteRepository courseNoteRepository;
    private final QuestionChapterRepository questionChapterRepository;
    private final ExerciseChapterRepository exerciseChapterRepository;

    public CourseChapterServiceImpl(CourseChapterRepository chapterRepository,
                                     CourseRepository courseRepository,
                                     VideoRepository videoRepository,
                                     ExerciseRepository exerciseRepository,
                                     ChapterOfflineSessionRepository chapterOfflineSessionRepository,
                                     LearningProgressRepository learningProgressRepository,
                                     CourseNoteRepository courseNoteRepository,
                                     QuestionChapterRepository questionChapterRepository,
                                     ExerciseChapterRepository exerciseChapterRepository) {
        this.chapterRepository = chapterRepository;
        this.courseRepository = courseRepository;
        this.videoRepository = videoRepository;
        this.exerciseRepository = exerciseRepository;
        this.chapterOfflineSessionRepository = chapterOfflineSessionRepository;
        this.learningProgressRepository = learningProgressRepository;
        this.courseNoteRepository = courseNoteRepository;
        this.questionChapterRepository = questionChapterRepository;
        this.exerciseChapterRepository = exerciseChapterRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<ChapterVO> page(int page, int size, Long courseId) {
        LambdaQueryWrapper<CourseChapter> wrapper = new LambdaQueryWrapper<>();
        if (courseId != null) {
            wrapper.eq(CourseChapter::getCourseId, courseId);
        }
        wrapper.orderByAsc(CourseChapter::getSortOrder);

        IPage<CourseChapter> ipage = chapterRepository.selectPage(
                new Page<>(page + 1, size), wrapper);

        List<Long> chapterIds = ipage.getRecords().stream()
                .map(CourseChapter::getId).collect(Collectors.toList());
        java.util.Map<Long, Long> videoCountMap = batchCountVideosByChapter(chapterIds);

        List<ChapterVO> vos = ipage.getRecords().stream()
                .map(ch -> {
                    ChapterVO vo = convertToVO(ch);
                    vo.setVideoCount(videoCountMap.getOrDefault(ch.getId(), 0L).intValue());
                    return vo;
                })
                .collect(Collectors.toList());

        PageResult<ChapterVO> result = new PageResult<>();
        result.setItems(vos);
        result.setPage(page);
        result.setSize(size);
        result.setTotalElements(ipage.getTotal());
        result.setTotalPages(ipage.getPages());
        return result;
    }

    private java.util.Map<Long, Long> batchCountVideosByChapter(List<Long> chapterIds) {
        if (chapterIds == null || chapterIds.isEmpty()) return java.util.Collections.emptyMap();
        List<com.microcourse.entity.Video> videos = videoRepository.selectList(
                new LambdaQueryWrapper<com.microcourse.entity.Video>()
                        .select(com.microcourse.entity.Video::getChapterId)
                        .in(com.microcourse.entity.Video::getChapterId, chapterIds));
        return videos.stream()
                .filter(v -> v.getChapterId() != null)
                .collect(Collectors.groupingBy(com.microcourse.entity.Video::getChapterId, Collectors.counting()));
    }

    @Override
    @Transactional(readOnly = true)
    public ChapterVO getById(Long id) {
        CourseChapter chapter = chapterRepository.selectById(id);
        if (chapter == null) {
            throw new BusinessException(ErrorCode.CHAPTER_NOT_FOUND);
        }
        ChapterVO vo = convertToVO(chapter);
        Long vc = videoRepository.selectCount(
            new LambdaQueryWrapper<com.microcourse.entity.Video>()
                .eq(com.microcourse.entity.Video::getChapterId, id));
        vo.setVideoCount(vc == null ? 0 : vc.intValue());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChapterVO create(ChapterCreateRequest request) {
        // Validate course exists
        Course course = courseRepository.selectById(request.getCourseId());
        if (course == null) {
            throw new BusinessException(ErrorCode.CHAPTER_COURSE_NOT_FOUND);
        }
        // Owner check: only course teacher or ADMIN can create chapter
        assertCourseOwner(course);

        CourseChapter chapter = new CourseChapter();
        chapter.setCourseId(request.getCourseId());
        chapter.setTitle(request.getTitle());
        chapter.setDescription(request.getDescription());
        // 自动分配不重复的sortOrder,避免 UNIQUE(course_id,sort_order) 冲突
        Integer sortOrder = request.getSortOrder();
        // 自动分配不重复的sortOrder,避免 UNIQUE(course_id,sort_order) 冲突
        Long conflictCount = (sortOrder != null && sortOrder > 0) ? chapterRepository.selectCount(
            new LambdaQueryWrapper<CourseChapter>()
                .eq(CourseChapter::getCourseId, request.getCourseId())
                .eq(CourseChapter::getSortOrder, sortOrder)) : 1L;
        if (sortOrder == null || sortOrder <= 0 || conflictCount > 0) {
            // 查询该课程的最大sortOrder
            CourseChapter maxChapter = chapterRepository.selectOne(
                new LambdaQueryWrapper<CourseChapter>()
                    .eq(CourseChapter::getCourseId, request.getCourseId())
                    .orderByDesc(CourseChapter::getSortOrder)
                    .last("LIMIT 1"));
            sortOrder = (maxChapter != null ? maxChapter.getSortOrder() : 0) + 1;
        }
        chapter.setSortOrder(sortOrder);
        String chapterType = request.getChapterType() != null ? request.getChapterType() : "VIDEO";
        validateChapterType(chapterType);
        chapter.setChapterType(chapterType);
        chapter.setDuration(request.getDuration());
        chapter.setCreatedAt(LocalDateTime.now());
        chapter.setUpdatedAt(LocalDateTime.now());

        chapterRepository.insert(chapter);
        ChapterVO vo = convertToVO(chapter);
        // 新建章节时 videoCount 默认为 0
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChapterVO update(Long id, ChapterUpdateRequest request) {
        CourseChapter chapter = chapterRepository.selectById(id);
        if (chapter == null) {
            throw new BusinessException(ErrorCode.CHAPTER_NOT_FOUND);
        }
        // Owner check: only course teacher or ADMIN can update chapter
        assertCourseOwnerByCourseId(chapter.getCourseId());

        // Partial update
        if (request.getTitle() != null) chapter.setTitle(request.getTitle());
        if (request.getDescription() != null) chapter.setDescription(request.getDescription());
        if (request.getSortOrder() != null) chapter.setSortOrder(request.getSortOrder());
        if (request.getChapterType() != null) {
            validateChapterType(request.getChapterType());
            chapter.setChapterType(request.getChapterType());
        }
        if (request.getDuration() != null) chapter.setDuration(request.getDuration());

        chapter.setUpdatedAt(LocalDateTime.now());

        chapterRepository.updateById(chapter);
        ChapterVO vo = convertToVO(chapter);
        Long vc = videoRepository.selectCount(
            new LambdaQueryWrapper<com.microcourse.entity.Video>()
                .eq(com.microcourse.entity.Video::getChapterId, id));
        vo.setVideoCount(vc == null ? 0 : vc.intValue());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        CourseChapter chapter = chapterRepository.selectById(id);
        if (chapter == null) {
            throw new BusinessException(ErrorCode.CHAPTER_NOT_FOUND);
        }
        // Owner check: only course teacher or ADMIN can delete chapter
        assertCourseOwnerByCourseId(chapter.getCourseId());
        // MISC-NEW-4 修复:级联删除章节下的视频/练习/线下活动,避免孤儿记录
        videoRepository.delete(new LambdaQueryWrapper<com.microcourse.entity.Video>()
                .eq(com.microcourse.entity.Video::getChapterId, id));
        exerciseRepository.delete(new LambdaQueryWrapper<com.microcourse.entity.Exercise>()
                .eq(com.microcourse.entity.Exercise::getChapterId, id));
        chapterOfflineSessionRepository.delete(new LambdaQueryWrapper<ChapterOfflineSession>()
                .eq(ChapterOfflineSession::getChapterId, id));
        learningProgressRepository.delete(new LambdaQueryWrapper<LearningProgress>()
                .eq(LearningProgress::getChapterId, id));
        courseNoteRepository.delete(new LambdaQueryWrapper<CourseNote>()
                .eq(CourseNote::getChapterId, id));
        // P1-C: 补全章节删除级联 — 清理题目-章节和练习-章节关联
        questionChapterRepository.delete(new LambdaQueryWrapper<QuestionChapter>()
                .eq(QuestionChapter::getChapterId, id));
        exerciseChapterRepository.delete(new LambdaQueryWrapper<ExerciseChapter>()
                .eq(ExerciseChapter::getChapterId, id));
        chapterRepository.deleteById(id);
    }

    /**
     * 更新所有章节排序：使用逐条 updateById（安全替代 CASE WHEN 字符串拼接，消除 SQL 注入风险）。
     * P3 性能优化：使用 Map 查找替代双重循环 O(N*M) → O(N+M)。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sort(List<ChapterSortRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        List<Long> ids = requests.stream().map(ChapterSortRequest::getId).collect(Collectors.toList());
        List<CourseChapter> chapters = chapterRepository.selectBatchIds(ids);

        // 校验所有章节存在且属于同一课程
        if (chapters.isEmpty()) return;
        Set<Long> courseIds = chapters.stream()
                .map(CourseChapter::getCourseId)
                .collect(Collectors.toSet());
        if (courseIds.size() > 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "章节必须属于同一课程");
        }
        Long courseId = courseIds.iterator().next();
        assertCourseOwnerByCourseId(courseId);

        // P0-SEC-FIX: 使用逐条 updateById 替代 CASE WHEN 字符串拼接，消除 SQL 注入风险
        // P3 性能优化: 使用 Map 查找替代双重循环 O(N*M) → O(N+M)
        Map<Long, CourseChapter> chapterMap = chapters.stream()
                .collect(Collectors.toMap(CourseChapter::getId, Function.identity()));
        for (ChapterSortRequest r : requests) {
            CourseChapter chapter = chapterMap.get(r.getId());
            if (chapter != null) {
                chapter.setSortOrder(r.getSortOrder());
                chapter.setUpdatedAt(LocalDateTime.now());
                chapterRepository.updateById(chapter);
            }
        }
    }

    private ChapterVO convertToVO(CourseChapter chapter) {
        ChapterVO vo = new ChapterVO();
        vo.setId(chapter.getId());
        vo.setCourseId(chapter.getCourseId());
        vo.setTitle(chapter.getTitle());
        vo.setDescription(chapter.getDescription());
        vo.setSortOrder(chapter.getSortOrder());
        vo.setChapterType(chapter.getChapterType());
        vo.setDuration(chapter.getDuration());
        vo.setLearningObjectives(chapter.getLearningObjectives());
        vo.setCreatedAt(chapter.getCreatedAt());
        vo.setUpdatedAt(chapter.getUpdatedAt());
        vo.setVersion(chapter.getVersion());
        return vo;
    }

    /**
     * 校验当前用户是否为课程 owner（课程创建教师）或 ADMIN。
     * <p>通用模式：实现逻辑与 ExerciseServiceImpl / VideoServiceImpl / OfflineSessionServiceImpl /
     * LessonServiceImpl / QuestionServiceImpl 中的同名方法一致。若需统一重构，可抽取到公共工具类。</p>
     *
     * @param courseId 课程 ID
     * @throws BusinessException NOT_FOUND 课程不存在，NO_PERMISSION 无权限
     */
    private void assertCourseOwnerByCourseId(Long courseId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        assertCourseOwner(course);
    }

    /**
     * 校验当前用户是否为课程 owner（课程创建教师）或 ADMIN。
     * <p>通用模式：实现逻辑与 VideoServiceImpl.assertCourseOwner(Course) 一致。</p>
     *
     * @param course 课程实体（非 null）
     * @throws BusinessException NO_PERMISSION 无权限
     */
    private void assertCourseOwner(Course course) {
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
    }

    private static final java.util.Set<String> VALID_CHAPTER_TYPES = java.util.Set.of(
        "VIDEO", "INTERACTIVE", "EXERCISE", "OFFLINE"
    );

    private void validateChapterType(String chapterType) {
        if (chapterType != null && !VALID_CHAPTER_TYPES.contains(chapterType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "无效的章节类型: " + chapterType);
        }
    }
}