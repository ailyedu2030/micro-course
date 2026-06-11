package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.microcourse.dto.LearningProgressVO;
import com.microcourse.dto.ProgressCreateRequest;
import com.microcourse.dto.ProgressUpdateRequest;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseChapter;
import com.microcourse.entity.LearningProgress;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.LearningProgressRepository;
import com.microcourse.service.LearningProgressService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class LearningProgressServiceImpl implements LearningProgressService {

    private final LearningProgressRepository learningProgressRepository;
    private final CourseRepository courseRepository;
    private final CourseChapterRepository courseChapterRepository;

    public LearningProgressServiceImpl(LearningProgressRepository learningProgressRepository,
                                       CourseRepository courseRepository,
                                       CourseChapterRepository courseChapterRepository) {
        this.learningProgressRepository = learningProgressRepository;
        this.courseRepository = courseRepository;
        this.courseChapterRepository = courseChapterRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LearningProgressVO> getByUserAndCourse(Long userId, Long courseId) {
        LambdaQueryWrapper<LearningProgress> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningProgress::getUserId, userId)
               .eq(LearningProgress::getCourseId, courseId);
        List<LearningProgress> list = learningProgressRepository.selectList(wrapper);
        return list.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    @Transactional
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
        if (request.getTotalWatchTime() != null) {
            wrapper.set(LearningProgress::getTotalWatchTime, request.getTotalWatchTime());
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
    @Transactional
    public LearningProgressVO create(ProgressCreateRequest request) {
        LearningProgress progress = new LearningProgress();
        progress.setUserId(request.getUserId());
        progress.setCourseId(request.getCourseId());
        progress.setChapterId(request.getChapterId());
        progress.setVideoProgress(request.getVideoProgress());
        progress.setVideoPosition(request.getVideoPosition());
        progress.setExerciseCompleted(request.getExerciseCompleted());
        progress.setExercisePassed(request.getExercisePassed());
        progress.setTotalWatchTime(request.getTotalWatchTime());
        progress.setDeviceId(request.getDeviceId());
        progress.setPlatform(request.getPlatform());
        progress.setPlaybackSpeed(request.getPlaybackSpeed());
        progress.setConfidence(request.getConfidence());
        progress.setCompleted(false);
        progress.setLastWatchAt(LocalDateTime.now());
        progress.setCreatedAt(LocalDateTime.now());
        progress.setUpdatedAt(LocalDateTime.now());
        learningProgressRepository.insert(progress);
        return convertToVO(progress);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getCourseCompletion(Long userId, Long courseId) {
        LambdaQueryWrapper<LearningProgress> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningProgress::getUserId, userId)
               .eq(LearningProgress::getCourseId, courseId)
               .eq(LearningProgress::getCompleted, true);
        long completedCount = learningProgressRepository.selectCount(wrapper);

        LambdaQueryWrapper<CourseChapter> chapterWrapper = new LambdaQueryWrapper<>();
        chapterWrapper.eq(CourseChapter::getCourseId, courseId);
        long totalChapters = courseChapterRepository.selectCount(chapterWrapper);

        double completion = totalChapters == 0 ? 0.0 : (double) completedCount / totalChapters;
        Map<String, Object> result = new HashMap<>();
        result.put("completedCount", completedCount);
        result.put("totalChapters", totalChapters);
        result.put("completion", completion);
        return result;
    }

    private LearningProgressVO convertToVO(LearningProgress progress) {
        LearningProgressVO vo = new LearningProgressVO();
        vo.setId(progress.getId());
        vo.setUserId(progress.getUserId());
        vo.setCourseId(progress.getCourseId());
        vo.setChapterId(progress.getChapterId());
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
}