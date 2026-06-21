package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.microcourse.dto.LearningProgressVO;
import com.microcourse.dto.ProgressCreateRequest;
import com.microcourse.dto.ProgressUpdateRequest;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseChapter;
import com.microcourse.entity.LearningProgress;
import com.microcourse.entity.Video;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.LearningProgressRepository;
import com.microcourse.repository.VideoRepository;
import com.microcourse.service.LearningProgressService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LearningProgressServiceImpl implements LearningProgressService {

    private final LearningProgressRepository learningProgressRepository;
    private final CourseRepository courseRepository;
    private final CourseChapterRepository courseChapterRepository;
    private final VideoRepository videoRepository;

    public LearningProgressServiceImpl(LearningProgressRepository learningProgressRepository,
                                       CourseRepository courseRepository,
                                       CourseChapterRepository courseChapterRepository,
                                       VideoRepository videoRepository) {
        this.learningProgressRepository = learningProgressRepository;
        this.courseRepository = courseRepository;
        this.courseChapterRepository = courseChapterRepository;
        this.videoRepository = videoRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LearningProgressVO> getByUserAndCourse(Long userId, Long courseId) {
        LambdaQueryWrapper<LearningProgress> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningProgress::getUserId, userId)
               .eq(LearningProgress::getCourseId, courseId);
        List<LearningProgress> list = learningProgressRepository.selectList(wrapper);
        return convertToVOList(list);
    }

    private List<LearningProgressVO> convertToVOList(List<LearningProgress> list) {
        if (list.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        // N+1 修复：批量预加载 course 和 chapter
        Set<Long> courseIds = list.stream()
                .map(LearningProgress::getCourseId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> chapterIds = list.stream()
                .map(LearningProgress::getChapterId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Course> courseMap = new HashMap<>();
        Map<Long, CourseChapter> chapterMap = new HashMap<>();

        if (!courseIds.isEmpty()) {
            courseRepository.selectBatchIds(courseIds).forEach(c -> courseMap.put(c.getId(), c));
        }
        if (!chapterIds.isEmpty()) {
            courseChapterRepository.selectBatchIds(chapterIds).forEach(ch -> chapterMap.put(ch.getId(), ch));
        }

        final Map<Long, Course> finalCourseMap = courseMap;
        final Map<Long, CourseChapter> finalChapterMap = chapterMap;

        return list.stream()
                .map(p -> convertToVO(p, finalCourseMap, finalChapterMap))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProgress(Long id, Long userId, ProgressUpdateRequest request) {
        LearningProgress progress = learningProgressRepository.selectById(id);
        if (progress == null || !progress.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.LEARNING_PROGRESS_NOT_FOUND);
        }

        LambdaUpdateWrapper<LearningProgress> wrapper = new LambdaUpdateWrapper<>();
        if (request.getVideoProgress() != null) {
            wrapper.set(LearningProgress::getVideoProgress, request.getVideoProgress());
        }
        if (request.getVideoPosition() != null) {
            wrapper.set(LearningProgress::getVideoPosition, request.getVideoPosition());
        }
        if (request.getExerciseCompleted() != null) {
            wrapper.set(LearningProgress::getExerciseCompleted, request.getExerciseCompleted());
        }
        if (request.getExercisePassed() != null) {
            wrapper.set(LearningProgress::getExercisePassed, request.getExercisePassed());
        }
        if (request.getLessonId() != null) {
            wrapper.set(LearningProgress::getLessonId, request.getLessonId());
        }
        // 总观看时间:任何客户端上报都走原子累加,避免多设备并发覆盖丢失(CON-003 修复)
        if (request.getWatchDelta() != null && request.getWatchDelta() > 0) {
            wrapper.setSql(true, "total_watch_time = COALESCE(total_watch_time, 0) + {0}", request.getWatchDelta());
        } else if (request.getTotalWatchTime() != null && request.getTotalWatchTime() > 0) {
            // 兼容旧客户端字段:旧客户端把 totalWatchTime 当绝对值上传,改用 GREATEST 取最大值,
            // 避免每次上报都累加导致数据翻倍
            wrapper.setSql(true, "total_watch_time = GREATEST(COALESCE(total_watch_time, 0), {0})", request.getTotalWatchTime());
        }
        if (request.getDeviceId() != null) {
            wrapper.set(LearningProgress::getDeviceId, request.getDeviceId());
        }
        if (request.getPlatform() != null) {
            wrapper.set(LearningProgress::getPlatform, request.getPlatform());
        }
        if (request.getPlaybackSpeed() != null) {
            wrapper.set(LearningProgress::getPlaybackSpeed, request.getPlaybackSpeed());
        }
        if (request.getConfidence() != null) {
            wrapper.set(LearningProgress::getConfidence, request.getConfidence());
        }
        if (request.getCompleted() != null) {
            wrapper.set(LearningProgress::getCompleted, request.getCompleted());
        }
        wrapper.set(LearningProgress::getLastWatchAt, LocalDateTime.now());
        wrapper.set(LearningProgress::getUpdatedAt, LocalDateTime.now());
        wrapper.eq(LearningProgress::getId, id);
        learningProgressRepository.update(null, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LearningProgressVO create(ProgressCreateRequest request) {
        // Validate courseId exists
        if (request.getCourseId() == null || courseRepository.selectById(request.getCourseId()) == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        // Validate chapterId exists if provided (FK constraint)
        if (request.getChapterId() != null) {
            CourseChapter chapter = courseChapterRepository.selectById(request.getChapterId());
            if (chapter == null) {
                throw new BusinessException(ErrorCode.CHAPTER_NOT_FOUND);
            }
        }

        LearningProgress progress = new LearningProgress();
        progress.setUserId(request.getUserId());
        progress.setCourseId(request.getCourseId());
        progress.setChapterId(request.getChapterId());
        progress.setLessonId(request.getLessonId());
        progress.setVideoProgress(request.getVideoProgress());
        progress.setVideoPosition(request.getVideoPosition());
        progress.setExerciseCompleted(request.getExerciseCompleted());
        progress.setExercisePassed(request.getExercisePassed());
        progress.setTotalWatchTime(request.getTotalWatchTime());
        progress.setDeviceId(request.getDeviceId());
        progress.setPlatform(request.getPlatform());
        progress.setPlaybackSpeed(request.getPlaybackSpeed());
        progress.setConfidence(request.getConfidence());
        progress.setCompleted(request.getCompleted() != null ? request.getCompleted() : false);
        progress.setLastWatchAt(LocalDateTime.now());
        progress.setCreatedAt(LocalDateTime.now());
        progress.setUpdatedAt(LocalDateTime.now());
        learningProgressRepository.insert(progress);
        // N+1 修复:使用批量版 convertToVO,避免每次 create 触发 2 次 selectById
        Map<Long, Course> courseMap = new HashMap<>();
        Map<Long, CourseChapter> chapterMap = new HashMap<>();
        if (progress.getCourseId() != null) {
            Course course = courseRepository.selectById(progress.getCourseId());
            if (course != null) {
                courseMap.put(course.getId(), course);
            }
        }
        if (progress.getChapterId() != null) {
            CourseChapter ch = courseChapterRepository.selectById(progress.getChapterId());
            if (ch != null) {
                chapterMap.put(ch.getId(), ch);
            }
        }
        return convertToVO(progress, courseMap, chapterMap);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getCourseCompletion(Long userId, Long courseId) {
        LambdaQueryWrapper<LearningProgress> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningProgress::getUserId, userId)
               .eq(LearningProgress::getCourseId, courseId)
               .eq(LearningProgress::getCompleted, true);
        long completedCount = learningProgressRepository.selectCount(wrapper);

        long totalVideos = videoRepository.selectCount(
                new LambdaQueryWrapper<Video>().eq(Video::getCourseId, courseId));
        long totalProgressItems = learningProgressRepository.selectCount(
                new LambdaQueryWrapper<LearningProgress>()
                        .eq(LearningProgress::getUserId, userId)
                        .eq(LearningProgress::getCourseId, courseId));

        double completion = totalVideos == 0 ? 0.0 : (double) completedCount / totalVideos;
        Map<String, Object> result = new HashMap<>();
        result.put("completedCount", completedCount);
        result.put("totalLessons", totalVideos);
        result.put("startedLessons", totalProgressItems);
        result.put("completion", Math.min(completion, 1.0));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getStudyDays(Long userId) {
        // MISC-NEW-6 修复:改用 SQL COUNT(DISTINCT),避免全量加载到 Java 内存
        QueryWrapper<LearningProgress> qw = new QueryWrapper<>();
        qw.select("COUNT(DISTINCT DATE(last_watch_at)) as days")
                .eq("user_id", userId)
                .isNotNull("last_watch_at");
        List<Map<String, Object>> rows = learningProgressRepository.selectMaps(qw);
        long totalDays = rows.isEmpty() ? 0L
                : ((Number) rows.get(0).getOrDefault("days", 0L)).longValue();

        Map<String, Object> result = new HashMap<>();
        result.put("totalDays", totalDays);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getTotalTime(Long userId) {
        // MISC-NEW-6 修复:改用 SQL SUM,避免全量加载到 Java 内存
        QueryWrapper<LearningProgress> qw = new QueryWrapper<>();
        qw.select("COALESCE(SUM(total_watch_time), 0) as total")
                .eq("user_id", userId);
        List<Map<String, Object>> rows = learningProgressRepository.selectMaps(qw);
        int totalSeconds = rows.isEmpty() ? 0
                : ((Number) rows.get(0).getOrDefault("total", 0)).intValue();

        Map<String, Object> result = new HashMap<>();
        result.put("totalSeconds", totalSeconds);
        return result;
    }

    private LearningProgressVO convertToVO(LearningProgress progress) {
        LearningProgressVO vo = new LearningProgressVO();
        vo.setId(progress.getId());
        vo.setUserId(progress.getUserId());
        vo.setCourseId(progress.getCourseId());
        vo.setChapterId(progress.getChapterId());
        vo.setLessonId(progress.getLessonId());
        vo.setVideoProgress(progress.getVideoProgress());
        vo.setVideoPosition(progress.getVideoPosition());
        vo.setExerciseCompleted(progress.getExerciseCompleted());
        vo.setExercisePassed(progress.getExercisePassed());
        vo.setTotalWatchTime(progress.getTotalWatchTime());
        vo.setDeviceId(progress.getDeviceId());
        vo.setPlatform(progress.getPlatform());
        vo.setPlaybackSpeed(progress.getPlaybackSpeed());
        vo.setConfidence(progress.getConfidence());
        vo.setCompleted(progress.getCompleted());
        vo.setLastWatchAt(progress.getLastWatchAt());
        vo.setCreatedAt(progress.getCreatedAt());
        vo.setUpdatedAt(progress.getUpdatedAt());

        if (progress.getCourseId() != null) {
            Course course = courseRepository.selectById(progress.getCourseId());
            if (course != null) {
                vo.setCourseTitle(course.getTitle());
            }
        }
        if (progress.getChapterId() != null) {
            CourseChapter chapter = courseChapterRepository.selectById(progress.getChapterId());
            if (chapter != null) {
                vo.setChapterTitle(chapter.getTitle());
            }
        }
        return vo;
    }

    private LearningProgressVO convertToVO(LearningProgress progress, Map<Long, Course> courseMap,
                                             Map<Long, CourseChapter> chapterMap) {
        LearningProgressVO vo = new LearningProgressVO();
        vo.setId(progress.getId());
        vo.setUserId(progress.getUserId());
        vo.setCourseId(progress.getCourseId());
        vo.setChapterId(progress.getChapterId());
        vo.setLessonId(progress.getLessonId());
        vo.setVideoProgress(progress.getVideoProgress());
        vo.setVideoPosition(progress.getVideoPosition());
        vo.setExerciseCompleted(progress.getExerciseCompleted());
        vo.setExercisePassed(progress.getExercisePassed());
        vo.setTotalWatchTime(progress.getTotalWatchTime());
        vo.setDeviceId(progress.getDeviceId());
        vo.setPlatform(progress.getPlatform());
        vo.setPlaybackSpeed(progress.getPlaybackSpeed());
        vo.setConfidence(progress.getConfidence());
        vo.setCompleted(progress.getCompleted());
        vo.setLastWatchAt(progress.getLastWatchAt());
        vo.setCreatedAt(progress.getCreatedAt());
        vo.setUpdatedAt(progress.getUpdatedAt());

        if (progress.getCourseId() != null) {
            Course course = courseMap.get(progress.getCourseId());
            if (course != null) {
                vo.setCourseTitle(course.getTitle());
            }
        }
        if (progress.getChapterId() != null) {
            CourseChapter chapter = chapterMap.get(progress.getChapterId());
            if (chapter != null) {
                vo.setChapterTitle(chapter.getTitle());
            }
        }
        return vo;
    }
}