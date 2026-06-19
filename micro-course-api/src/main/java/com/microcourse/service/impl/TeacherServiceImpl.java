package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.*;
import com.microcourse.entity.*;
import com.microcourse.repository.*;
import com.microcourse.service.EnrollmentService;
import com.microcourse.service.TeacherService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TeacherServiceImpl implements TeacherService {

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ExerciseRecordRepository exerciseRecordRepository;
    private final LearningProgressRepository learningProgressRepository;
    private final NotificationRepository notificationRepository;
    private final DiscussionPostRepository discussionPostRepository;
    private final DiscussionCommentRepository discussionCommentRepository;
    private final ExerciseRepository exerciseRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final EnrollmentService enrollmentService;

    public TeacherServiceImpl(
            CourseRepository courseRepository,
            EnrollmentRepository enrollmentRepository,
            ExerciseRecordRepository exerciseRecordRepository,
            LearningProgressRepository learningProgressRepository,
            NotificationRepository notificationRepository,
            DiscussionPostRepository discussionPostRepository,
            DiscussionCommentRepository discussionCommentRepository,
            ExerciseRepository exerciseRepository,
            QuestionRepository questionRepository,
            UserRepository userRepository,
            EnrollmentService enrollmentService) {
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.exerciseRecordRepository = exerciseRecordRepository;
        this.learningProgressRepository = learningProgressRepository;
        this.notificationRepository = notificationRepository;
        this.discussionPostRepository = discussionPostRepository;
        this.discussionCommentRepository = discussionCommentRepository;
        this.exerciseRepository = exerciseRepository;
        this.questionRepository = questionRepository;
        this.userRepository = userRepository;
        this.enrollmentService = enrollmentService;
    }

    @Override
    @Transactional(readOnly = true)
    public TeacherStatsVO getStats(Long teacherId) {
        TeacherStatsVO stats = new TeacherStatsVO();

        // 课程数
        long courseCount = courseRepository.selectCount(
            new LambdaQueryWrapper<Course>()
                .eq(Course::getTeacherId, teacherId)
                .isNull(Course::getDeletedAt));
        stats.setCourseCount((int) courseCount);

        // 学员数（该教师所有课程的选课人数）
        List<Long> courseIds = courseRepository.selectList(
            new LambdaQueryWrapper<Course>()
                .eq(Course::getTeacherId, teacherId)
                .isNull(Course::getDeletedAt)
                .select(Course::getId))
            .stream().map(Course::getId).collect(Collectors.toList());

        long studentCount = 0;
        if (!courseIds.isEmpty()) {
            studentCount = enrollmentRepository.selectCount(
                new LambdaQueryWrapper<Enrollment>()
                    .in(Enrollment::getCourseId, courseIds)
                    .isNull(Enrollment::getDeletedAt));
        }
        stats.setStudentCount((int) studentCount);

        // 待批改作业（已提交但未批改的练习记录）
        if (!courseIds.isEmpty()) {
            List<Long> exerciseIds = exerciseRepository.selectList(
                new LambdaQueryWrapper<Exercise>()
                    .in(Exercise::getCourseId, courseIds)
                    .isNull(Exercise::getDeletedAt)
                    .select(Exercise::getId))
                .stream().map(Exercise::getId).collect(Collectors.toList());

            long pendingHomework = 0;
            if (!exerciseIds.isEmpty()) {
                pendingHomework = exerciseRecordRepository.selectCount(
                    new LambdaQueryWrapper<ExerciseRecord>()
                        .in(ExerciseRecord::getExerciseId, exerciseIds)
                        .isNull(ExerciseRecord::getScore)
                        .isNotNull(ExerciseRecord::getSubmittedAt)
                        .isNull(ExerciseRecord::getDeletedAt));
            }
            stats.setPendingHomework((int) pendingHomework);
        } else {
            stats.setPendingHomework(0);
        }

        // 学员提问（未回复的讨论帖：course内帖子中，无教师回复的帖子）
        if (!courseIds.isEmpty()) {
            // 找出所有有教师回复的帖子ID
            List<Long> postIds = discussionPostRepository.selectList(
                new LambdaQueryWrapper<DiscussionPost>()
                    .in(DiscussionPost::getCourseId, courseIds)
                    .isNull(DiscussionPost::getDeletedAt)
                    .select(DiscussionPost::getId))
                .stream().map(DiscussionPost::getId).collect(Collectors.toList());

            Set<Long> repliedPostIds = postIds.isEmpty() ? Collections.emptySet() :
                discussionCommentRepository.selectList(
                    new LambdaQueryWrapper<DiscussionComment>()
                        .in(DiscussionComment::getPostId, postIds)
                        .eq(DiscussionComment::getIsTeacherReply, true)
                        .isNull(DiscussionComment::getDeletedAt))
                .stream()
                .map(DiscussionComment::getPostId)
                .collect(Collectors.toSet());

            // 统计未被回复的帖子数
            long unansweredCount = discussionPostRepository.selectList(
                new LambdaQueryWrapper<DiscussionPost>()
                    .in(DiscussionPost::getCourseId, courseIds)
                    .isNull(DiscussionPost::getDeletedAt))
                .stream()
                .filter(p -> !repliedPostIds.contains(p.getId()))
                .count();
            stats.setPendingQuestions((int) unansweredCount);
        } else {
            stats.setPendingQuestions(0);
        }

        // 完成率
        long totalEnrollments = enrollmentService.countByTeacherId(teacherId);
        long completedEnrollments = enrollmentService.countCompletedByTeacherId(teacherId);
        double completionRate = totalEnrollments > 0 ? completedEnrollments * 100.0 / totalEnrollments : 0;
        stats.setCompletionRate(completionRate);

        // 平均分
        double avgScore = enrollmentService.getAvgScoreByTeacherId(teacherId);
        stats.setAvgScore(avgScore);

        return stats;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentActivityVO> getStudentActivity(Long teacherId, int days) {
        LocalDate today = LocalDate.now();
        LocalDateTime rangeStart = today.minusDays(days - 1).atStartOfDay();
        LocalDateTime rangeEnd = today.plusDays(1).atStartOfDay();

        List<Long> courseIds = courseRepository.selectList(
            new LambdaQueryWrapper<Course>()
                .eq(Course::getTeacherId, teacherId)
                .isNull(Course::getDeletedAt)
                .select(Course::getId))
            .stream().map(Course::getId).collect(Collectors.toList());

        // 一次查询整个时间范围，避免 N+1
        List<LearningProgress> allProgress = Collections.emptyList();
        if (!courseIds.isEmpty()) {
            allProgress = learningProgressRepository.selectList(
                new LambdaQueryWrapper<LearningProgress>()
                    .in(LearningProgress::getCourseId, courseIds)
                    .ge(LearningProgress::getLastWatchAt, rangeStart)
                    .lt(LearningProgress::getLastWatchAt, rangeEnd)
                    .isNull(LearningProgress::getDeletedAt));
        }

        // 按日期分组
        Map<LocalDate, List<LearningProgress>> grouped = allProgress.stream()
            .collect(Collectors.groupingBy(p -> p.getLastWatchAt().toLocalDate()));

        List<StudentActivityVO> result = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            String dateStr = date.format(DateTimeFormatter.ofPattern("MM-dd"));

            StudentActivityVO vo = new StudentActivityVO();
            vo.setDate(dateStr);

            List<LearningProgress> dayProgress = grouped.getOrDefault(date, Collections.emptyList());

            int totalMinutes = dayProgress.stream()
                .mapToInt(p -> p.getTotalWatchTime() != null ? p.getTotalWatchTime() : 0)
                .sum();
            vo.setStudyMinutes(totalMinutes / 60);

            long activeUsers = dayProgress.stream()
                .map(LearningProgress::getUserId)
                .distinct()
                .count();
            vo.setActiveUsers((int) activeUsers);

            long completed = dayProgress.stream()
                .filter(p -> Boolean.TRUE.equals(p.getCompleted()))
                .count();
            int completionRate = dayProgress.isEmpty() ? 0 :
                (int) (completed * 100 / dayProgress.size());
            vo.setCompletionRate(completionRate);

            result.add(vo);
        }

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingTaskVO> getPendingTasks(Long teacherId, int size) {
        List<PendingTaskVO> tasks = new ArrayList<>();

        List<Long> courseIds = courseRepository.selectList(
            new LambdaQueryWrapper<Course>()
                .eq(Course::getTeacherId, teacherId)
                .isNull(Course::getDeletedAt)
                .select(Course::getId))
            .stream().map(Course::getId).collect(Collectors.toList());

        // 待批改练习（取最新几条）
        if (!courseIds.isEmpty()) {
            // 查找该教师课程下的所有练习ID
            List<Exercise> teacherExercises = exerciseRepository.selectList(
                new LambdaQueryWrapper<Exercise>()
                    .in(Exercise::getCourseId, courseIds)
                    .isNull(Exercise::getDeletedAt)
                    .select(Exercise::getId));
            List<Long> exerciseIds = teacherExercises.stream()
                .map(Exercise::getId)
                .collect(Collectors.toList());

            List<ExerciseRecord> records = new ArrayList<>();
            if (!exerciseIds.isEmpty()) {
                Page<ExerciseRecord> recordPage = new Page<>(1, Math.min(size, 100));
                exerciseRecordRepository.selectPage(recordPage,
                    new LambdaQueryWrapper<ExerciseRecord>()
                        .in(ExerciseRecord::getExerciseId, exerciseIds)
                        .isNull(ExerciseRecord::getDeletedAt)
                        .orderByDesc(ExerciseRecord::getSubmittedAt));
                records = recordPage.getRecords();
            }

            for (ExerciseRecord record : records) {
                PendingTaskVO task = new PendingTaskVO();
                task.setId(record.getId());
                task.setType("练习批改");
                task.setTitle("学员练习待批改");
                task.setCreatedAt(record.getSubmittedAt());
                tasks.add(task);
            }
        }

        // 未回复的讨论帖
        if (!courseIds.isEmpty()) {
            Page<DiscussionPost> postPage = new Page<>(1, Math.min(size, 100));
            discussionPostRepository.selectPage(postPage,
                new LambdaQueryWrapper<DiscussionPost>()
                    .in(DiscussionPost::getCourseId, courseIds)
                    .isNull(DiscussionPost::getDeletedAt)
                    .orderByDesc(DiscussionPost::getCreatedAt));
            List<DiscussionPost> posts = postPage.getRecords();

            for (DiscussionPost post : posts) {
                PendingTaskVO task = new PendingTaskVO();
                task.setId(post.getId());
                task.setType("学员提问");
                task.setTitle(post.getTitle());
                task.setCreatedAt(post.getCreatedAt());
                tasks.add(task);
            }
        }

        return tasks.stream().limit(size).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeacherNotificationVO> getNotifications(Long teacherId, int size) {
        User user = userRepository.selectById(teacherId);
        Long userId = user != null ? user.getId() : teacherId;

        Page<Notification> notifPage = new Page<>(1, Math.min(size, 100));
        notificationRepository.selectPage(notifPage,
            new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreatedAt));
        List<Notification> notifications = notifPage.getRecords();

        return notifications.stream().map(n -> {
            TeacherNotificationVO vo = new TeacherNotificationVO();
            vo.setId(n.getId());
            vo.setTitle(n.getTitle());
            vo.setContent(n.getContent());
            vo.setCreatedAt(n.getCreatedAt());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<TeacherCourseVO> getMyCourses(Long teacherId, int page, int size) {
        IPage<Course> coursePage = courseRepository.selectPage(
            new Page<>(page, size),
            new LambdaQueryWrapper<Course>()
                .eq(Course::getTeacherId, teacherId)
                .isNull(Course::getDeletedAt)
                .orderByDesc(Course::getCreatedAt));

        List<TeacherCourseVO> vos = coursePage.getRecords().stream().map(c -> {
            TeacherCourseVO vo = new TeacherCourseVO();
            vo.setId(c.getId());
            vo.setTitle(c.getTitle());
            String coverUrl = c.getCoverUrl();
            vo.setCover(coverUrl != null && coverUrl.startsWith("https://") ? coverUrl : null);
            vo.setStudentCount(c.getStudentCount());
            vo.setRating(c.getAvgRating());
            vo.setStatus(c.getStatus());
            return vo;
        }).collect(Collectors.toList());

        return PageResult.of(vos, coursePage.getTotal(), page, size);
    }
}