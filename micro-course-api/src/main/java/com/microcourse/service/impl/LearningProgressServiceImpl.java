package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.microcourse.dto.LearningProgressVO;
import com.microcourse.dto.ProgressCreateRequest;
import com.microcourse.dto.ProgressUpdateRequest;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseChapter;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.LearningProgress;
import com.microcourse.entity.Video;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
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
    private final EnrollmentRepository enrollmentRepository;

    public LearningProgressServiceImpl(LearningProgressRepository learningProgressRepository,
                                       CourseRepository courseRepository,
                                       CourseChapterRepository courseChapterRepository,
                                       VideoRepository videoRepository,
                                       EnrollmentRepository enrollmentRepository) {
        this.learningProgressRepository = learningProgressRepository;
        this.courseRepository = courseRepository;
        this.courseChapterRepository = courseChapterRepository;
        this.videoRepository = videoRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LearningProgressVO> getByUserAndCourse(Long userId, Long courseId) {
        LambdaQueryWrapper<LearningProgress> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningProgress::getUserId, userId)
               .eq(LearningProgress::getCourseId, courseId);
        List<LearningProgress> list = learningProgressRepository.selectList(wrapper);
        List<LearningProgressVO> vos = convertToVOList(list);

        // P0-4: 计算课程级练习完成统计
        int completedEx = 0;
        int totalEx = 0;
        int completedVideos = 0;
        for (LearningProgress p : list) {
            if (p.getLessonId() != null) { // 仅统计课时级记录
                totalEx++;
                if (Boolean.TRUE.equals(p.getExerciseCompleted())) {
                    completedEx++;
                }
                if (Boolean.TRUE.equals(p.getCompleted())) {
                    completedVideos++;
                }
            }
        }
        final int ce = completedEx;
        final int te = totalEx;
        final int cv = completedVideos;
        vos.forEach(vo -> {
            vo.setCompletedExercises(ce);
            vo.setTotalExercises(te);
            vo.setCompletedVideos(cv);
        });
        return vos;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LearningProgressVO> batchGetByUserAndCourses(Long userId, java.util.List<Long> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) return new java.util.ArrayList<>();
        LambdaQueryWrapper<LearningProgress> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningProgress::getUserId, userId)
               .in(LearningProgress::getCourseId, courseIds);
        java.util.List<LearningProgress> list = learningProgressRepository.selectList(wrapper);
        java.util.List<LearningProgressVO> vos = convertToVOList(list);

        // 按 courseId 聚合已完成视频数
        java.util.Map<Long, Integer> completedVideosByCourse = new java.util.HashMap<>();
        for (LearningProgress p : list) {
            if (p.getCourseId() != null && p.getLessonId() != null && Boolean.TRUE.equals(p.getCompleted())) {
                completedVideosByCourse.merge(p.getCourseId(), 1, Integer::sum);
            }
        }
        for (LearningProgressVO vo : vos) {
            if (vo.getCourseId() != null) {
                vo.setCompletedVideos(completedVideosByCourse.getOrDefault(vo.getCourseId(), 0));
            }
        }

        java.util.Map<Long, LearningProgressVO> byCourse = new java.util.HashMap<>();
        for (LearningProgressVO vo : vos) {
            byCourse.put(vo.getCourseId(), vo);
        }
        return courseIds.stream()
                .map(cid -> byCourse.get(cid))
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toList());
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

        // ★ Round 8-4 修复(P0)：多设备/并发重复上报防护。
        // learning_progress 仅对 lesson_id IS NOT NULL 有 DB 唯一约束(uk_lp_user_lesson)，
        // 章节级（lesson_id 为 null）记录此前无任何约束，并发 create 会产生重复记录、完成度翻倍。
        // 这里先按业务粒度查重：命中则幂等更新已有记录（合法用户体验零退化）；
        // 未命中再插入，极端并发命中 V66 部分唯一索引时兜底转 400 提示，绝不抛 500。
        // 这里先按业务粒度查重：命中则幂等更新已有记录（合法用户体验零退化）；
        // 未命中再走幂等 upsert（ON CONFLICT DO NOTHING），并发全部返回 200、最终仅 1 行。
        LearningProgress existing = learningProgressRepository.selectOne(dedupWrapper(request));
        if (existing != null) {
            // 幂等更新：仅覆盖客户端上报的非空字段，last_watch_at 始终刷新
            if (request.getVideoProgress() != null) existing.setVideoProgress(request.getVideoProgress());
            if (request.getVideoPosition() != null) existing.setVideoPosition(request.getVideoPosition());
            if (request.getTotalWatchTime() != null) existing.setTotalWatchTime(request.getTotalWatchTime());
            if (request.getLessonId() != null) existing.setLessonId(request.getLessonId());
            if (request.getExerciseCompleted() != null) existing.setExerciseCompleted(request.getExerciseCompleted());
            if (request.getExercisePassed() != null) existing.setExercisePassed(request.getExercisePassed());
            if (request.getDeviceId() != null) existing.setDeviceId(request.getDeviceId());
            if (request.getPlatform() != null) existing.setPlatform(request.getPlatform());
            if (request.getPlaybackSpeed() != null) existing.setPlaybackSpeed(request.getPlaybackSpeed());
            if (request.getConfidence() != null) existing.setConfidence(request.getConfidence());
            if (request.getCompleted() != null) existing.setCompleted(request.getCompleted());
            existing.setLastWatchAt(LocalDateTime.now());
            existing.setUpdatedAt(LocalDateTime.now());
            learningProgressRepository.updateById(existing);
            Map<Long, Course> exCourseMap = new HashMap<>();
            Map<Long, CourseChapter> exChapterMap = new HashMap<>();
            if (existing.getCourseId() != null) {
                Course c = courseRepository.selectById(existing.getCourseId());
                if (c != null) exCourseMap.put(c.getId(), c);
            }
            if (existing.getChapterId() != null) {
                CourseChapter ch = courseChapterRepository.selectById(existing.getChapterId());
                if (ch != null) exChapterMap.put(ch.getId(), ch);
            }
            return convertToVO(existing, exCourseMap, exChapterMap);
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
        // ★ Round 8-4 修复(P0)：幂等 upsert —— ON CONFLICT DO NOTHING 永不抛唯一约束异常
        // （PG 事务不会 abort）。并发 loser 会阻塞至 winner 提交后再回查命中，最终全部 200、仅 1 行，
        // 既彻底消除重复进度记录（完成度翻倍），又保证合法用户体验零退化（不抛 500/不返回 4xx）。
        learningProgressRepository.insertIfAbsent(progress);
        LearningProgress saved = learningProgressRepository.selectOne(dedupWrapper(request));
        if (saved == null) {
            // 极端边界（插入后被并发软删除）：友好提示重试，绝不抛 500
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "学习进度记录创建失败，请重试");
        }
        // N+1 修复:使用批量版 convertToVO,避免每次 create 触发 2 次 selectById
        Map<Long, Course> courseMap = new HashMap<>();
        Map<Long, CourseChapter> chapterMap = new HashMap<>();
        if (saved.getCourseId() != null) {
            Course course = courseRepository.selectById(saved.getCourseId());
            if (course != null) {
                courseMap.put(course.getId(), course);
            }
        }
        if (saved.getChapterId() != null) {
            CourseChapter ch = courseChapterRepository.selectById(saved.getChapterId());
            if (ch != null) {
                chapterMap.put(ch.getId(), ch);
            }
        }
        return convertToVO(saved, courseMap, chapterMap);
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
        // P0-6: 同时返回 progress 别名，兼容前端两种用法
        result.put("progress", Math.min(completion, 1.0));
        return result;
    }

    /**
     * P0-5: 聚合用户所有已选课程的完成进度
     */
    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getAllCourseCompletions(Long userId) {
        // 查询用户所有选课记录
        List<Enrollment> enrollments = enrollmentRepository.selectList(
                new LambdaQueryWrapper<Enrollment>().eq(Enrollment::getUserId, userId));
        Map<String, Object> result = new HashMap<>();
        for (Enrollment enrollment : enrollments) {
            Long cid = enrollment.getCourseId();
            Map<String, Object> single = getCourseCompletion(userId, cid);
            result.put(String.valueOf(cid), single);
        }
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

    @Override
    public void assertTeacherOwnsCourse(Long teacherId, Long courseId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null || !course.getTeacherId().equals(teacherId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
    }

    /**
     * 构建学习进度查重条件（Round 8-4 P0）：按 (user, course, chapter, lesson) 业务粒度匹配活跃记录，
     * chapter/lesson 为空时用 IS NULL 精确匹配，避免 NULL 误命中。@TableLogic 自动追加 deleted_at IS NULL。
     */
    private LambdaQueryWrapper<LearningProgress> dedupWrapper(ProgressCreateRequest request) {
        LambdaQueryWrapper<LearningProgress> w = new LambdaQueryWrapper<>();
        w.eq(LearningProgress::getUserId, request.getUserId())
         .eq(LearningProgress::getCourseId, request.getCourseId());
        if (request.getChapterId() != null) {
            w.eq(LearningProgress::getChapterId, request.getChapterId());
        } else {
            w.isNull(LearningProgress::getChapterId);
        }
        if (request.getLessonId() != null) {
            w.eq(LearningProgress::getLessonId, request.getLessonId());
        } else {
            w.isNull(LearningProgress::getLessonId);
        }
        w.orderByDesc(LearningProgress::getId).last("LIMIT 1");
        return w;
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