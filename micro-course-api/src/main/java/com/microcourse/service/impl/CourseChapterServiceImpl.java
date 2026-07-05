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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        wrapper.orderByAsc(CourseChapter::getSortOrder).orderByAsc(CourseChapter::getId);

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
        Course course = courseRepository.selectById(request.getCourseId());
        if (course == null) {
            throw new BusinessException(ErrorCode.CHAPTER_COURSE_NOT_FOUND);
        }
        assertCourseOwner(course);

        CourseChapter chapter = new CourseChapter();
        chapter.setCourseId(request.getCourseId());
        chapter.setTitle(request.getTitle());
        chapter.setDescription(request.getDescription());
        // sortOrder: 用户指定的值直接使用(约束已删除,不会冲突)
        // 未指定(0/null)时自动追加到末尾
        int sortOrder = request.getSortOrder() != null ? request.getSortOrder() : 0;
        if (sortOrder <= 0) {
            CourseChapter max = chapterRepository.selectOne(
                new LambdaQueryWrapper<CourseChapter>()
                    .eq(CourseChapter::getCourseId, request.getCourseId())
                    .orderByDesc(CourseChapter::getSortOrder));
            sortOrder = (max != null && max.getSortOrder() != null ? max.getSortOrder() : 0) + 1;
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
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChapterVO update(Long id, ChapterUpdateRequest request) {
        CourseChapter chapter = chapterRepository.selectById(id);
        if (chapter == null) {
            throw new BusinessException(ErrorCode.CHAPTER_NOT_FOUND);
        }
        assertCourseOwnerByCourseId(chapter.getCourseId());

        // Partial update — sortOrder直接设置(约束已删除,不会冲突)
        if (request.getSortOrder() != null) {
            chapter.setSortOrder(request.getSortOrder());
        }
        if (request.getTitle() != null) chapter.setTitle(request.getTitle());
        if (request.getDescription() != null) chapter.setDescription(request.getDescription());
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
        assertCourseOwnerByCourseId(chapter.getCourseId());
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
        questionChapterRepository.delete(new LambdaQueryWrapper<QuestionChapter>()
                .eq(QuestionChapter::getChapterId, id));
        exerciseChapterRepository.delete(new LambdaQueryWrapper<ExerciseChapter>()
                .eq(ExerciseChapter::getChapterId, id));
        chapterRepository.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sort(List<ChapterSortRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        List<Long> ids = requests.stream().map(ChapterSortRequest::getId).collect(Collectors.toList());
        List<CourseChapter> chapters = chapterRepository.selectBatchIds(ids);

        if (chapters.isEmpty()) return;
        Set<Long> courseIds = chapters.stream()
                .map(CourseChapter::getCourseId)
                .collect(Collectors.toSet());
        if (courseIds.size() > 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "章节必须属于同一课程");
        }
        Long courseId = courseIds.iterator().next();
        assertCourseOwnerByCourseId(courseId);

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

    private void assertCourseOwnerByCourseId(Long courseId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        assertCourseOwner(course);
    }

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
