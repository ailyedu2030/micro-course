package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.PendingTaskVO;
import com.microcourse.dto.StudentActivityVO;
import com.microcourse.dto.TeacherCourseVO;
import com.microcourse.dto.TeacherNotificationVO;
import com.microcourse.dto.TeacherRevenueVO;
import com.microcourse.dto.TeacherStatsVO;
import com.microcourse.entity.Course;
import com.microcourse.entity.DiscussionComment;
import com.microcourse.entity.DiscussionPost;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.Exercise;
import com.microcourse.entity.ExerciseRecord;
import com.microcourse.entity.LearningProgress;
import com.microcourse.entity.Notification;
import com.microcourse.entity.Order;
import com.microcourse.entity.Question;
import com.microcourse.entity.User;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.DiscussionCommentRepository;
import com.microcourse.repository.DiscussionPostRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.ExerciseRecordRepository;
import com.microcourse.repository.ExerciseRepository;
import com.microcourse.repository.LearningProgressRepository;
import com.microcourse.repository.NotificationRepository;
import com.microcourse.repository.OrderRepository;
import com.microcourse.repository.QuestionRepository;
import com.microcourse.repository.TeacherRatingRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.EnrollmentService;
import com.microcourse.service.TeacherService;
import com.microcourse.util.SecurityUtil;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
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
    private final TeacherRatingRepository teacherRatingRepository;
    private final OrderRepository orderRepository;
    private final com.microcourse.service.PlatformShareRateResolver rateResolver;
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
            TeacherRatingRepository teacherRatingRepository,
            OrderRepository orderRepository,
            com.microcourse.service.PlatformShareRateResolver rateResolver,
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
        this.teacherRatingRepository = teacherRatingRepository;
        this.orderRepository = orderRepository;
        this.rateResolver = rateResolver;
        this.enrollmentService = enrollmentService;
    }

    @Override
    @Transactional(readOnly = true)
    public TeacherStatsVO getStats(Long teacherId) {
        assertTeacherOwnership(teacherId, SecurityUtil.getCurrentUserId());
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

        // 完成率 / 平均分
        // ★ Round 9-1 修复(N+1)：原实现经 enrollmentService 的三个方法各自重复查询「教师课程 ID 集合」
        // （getCourseIdsByTeacherId 共 3 次额外 selectList）并重复统计总选课数；此处复用上方已加载的
        // courseIds 与 studentCount（其查询条件 courseId∈courseIds AND deleted_at IS NULL 与 countByTeacherId
        // 完全一致），改为基于 courseIds 直接聚合。计算结果与原逻辑逐字段等价，仅消除约 5 次冗余往返。
        long totalEnrollments = studentCount; // 等价于 countByTeacherId（同一查询条件，上方已执行）
        long completedEnrollments = 0;
        double avgScore = 0;
        if (!courseIds.isEmpty()) {
            completedEnrollments = enrollmentRepository.selectCount(
                new LambdaQueryWrapper<Enrollment>()
                    .in(Enrollment::getCourseId, courseIds)
                    .eq(Enrollment::getCompleted, true)
                    .isNull(Enrollment::getDeletedAt));
            List<Enrollment> scored = enrollmentRepository.selectList(
                new LambdaQueryWrapper<Enrollment>()
                    .in(Enrollment::getCourseId, courseIds)
                    .isNotNull(Enrollment::getFinalScore)
                    .isNull(Enrollment::getDeletedAt));
            avgScore = scored.stream()
                .filter(e -> e.getFinalScore() != null)
                .mapToDouble(e -> e.getFinalScore().doubleValue())
                .average()
                .orElse(0);
        }
        double completionRate = totalEnrollments > 0 ? completedEnrollments * 100.0 / totalEnrollments : 0;
        stats.setCompletionRate(completionRate);
        stats.setAvgScore(avgScore);

        return stats;
    }

    @Override
    @Transactional(readOnly = true)
    public TeacherRevenueVO getRevenue(Long teacherId) {
        assertTeacherOwnership(teacherId, SecurityUtil.getCurrentUserId());
        // 1) 获取教师所有课程
        List<Course> courses = courseRepository.selectList(
                new LambdaQueryWrapper<Course>()
                        .eq(Course::getTeacherId, teacherId)
                        .isNull(Course::getDeletedAt));
        List<Long> courseIds = courses.stream().map(Course::getId).collect(Collectors.toList());
        Map<Long, String> courseTitleMap = courses.stream()
                .collect(Collectors.toMap(Course::getId, Course::getTitle));

        TeacherRevenueVO vo = new TeacherRevenueVO();
        if (courseIds.isEmpty()) {
            vo.setTotalRevenue(java.math.BigDecimal.ZERO);
            vo.setPlatformShare(java.math.BigDecimal.ZERO);
            vo.setNetEarnings(java.math.BigDecimal.ZERO);
            vo.setOrderCount(0);
            vo.setStudentCount(0);
            vo.setCourseBreakdown(new ArrayList<>());
            vo.setRecentTransactions(new ArrayList<>());
            return vo;
        }

        // 2) 查询所有 PAID 订单
        List<Order> paidOrders = orderRepository.selectList(
                new LambdaQueryWrapper<Order>()
                        .in(Order::getCourseId, courseIds)
                        .eq(Order::getStatus, "PAID")
                        .orderByDesc(Order::getPaidAt));

        if (paidOrders.isEmpty()) {
            vo.setTotalRevenue(java.math.BigDecimal.ZERO);
            vo.setPlatformShare(java.math.BigDecimal.ZERO);
            vo.setNetEarnings(java.math.BigDecimal.ZERO);
            vo.setOrderCount(0);
            vo.setStudentCount(0);
            vo.setCourseBreakdown(new ArrayList<>());
            vo.setRecentTransactions(new ArrayList<>());
            return vo;
        }

        // 3) 聚合统计
        java.math.BigDecimal totalRevenue = java.math.BigDecimal.ZERO;
        Set<Long> uniqueStudents = new HashSet<>();
        Map<Long, List<Order>> ordersByCourse = paidOrders.stream()
                .collect(Collectors.groupingBy(Order::getCourseId));

        // 从教师等级获取实际分账率(修复 P0-1: 从 config 表读,不再硬编码)
        com.microcourse.entity.TeacherRating teacherRating = teacherRatingRepository.selectOne(
                new LambdaQueryWrapper<com.microcourse.entity.TeacherRating>()
                        .eq(com.microcourse.entity.TeacherRating::getTeacherId, teacherId));
        java.math.BigDecimal platformRate = teacherRating != null
                ? rateResolver.getRateByTier(teacherRating.getTier())
                : rateResolver.getDefaultGlobalRate();

        for (Order o : paidOrders) {
            totalRevenue = totalRevenue.add(o.getAmount() != null ? o.getAmount() : java.math.BigDecimal.ZERO);
            if (o.getUserId() != null) uniqueStudents.add(o.getUserId());
        }

        java.math.BigDecimal platformShare = totalRevenue.multiply(platformRate)
                .divide(java.math.BigDecimal.valueOf(100), java.math.RoundingMode.HALF_UP);
        java.math.BigDecimal netEarnings = totalRevenue.subtract(platformShare);

        vo.setTotalRevenue(totalRevenue);
        vo.setPlatformShare(platformShare);
        vo.setNetEarnings(netEarnings);
        vo.setOrderCount(paidOrders.size());
        vo.setStudentCount(uniqueStudents.size());

        // 4) 按课程分解
        List<TeacherRevenueVO.CourseRevenueItem> breakdown = new ArrayList<>();
        for (Map.Entry<Long, List<Order>> entry : ordersByCourse.entrySet()) {
            TeacherRevenueVO.CourseRevenueItem item = new TeacherRevenueVO.CourseRevenueItem();
            item.setCourseId(entry.getKey());
            item.setCourseTitle(courseTitleMap.getOrDefault(entry.getKey(), "未知课程"));
            java.math.BigDecimal courseRevenue = entry.getValue().stream()
                    .map(o -> o.getAmount() != null ? o.getAmount() : java.math.BigDecimal.ZERO)
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            item.setRevenue(courseRevenue);
            item.setOrderCount(entry.getValue().size());
            java.math.BigDecimal coursePlatform = courseRevenue.multiply(platformRate)
                    .divide(java.math.BigDecimal.valueOf(100), java.math.RoundingMode.HALF_UP);
            item.setPlatformShare(coursePlatform);
            item.setNetEarnings(courseRevenue.subtract(coursePlatform));
            breakdown.add(item);
        }
        breakdown.sort((a, b) -> b.getRevenue().compareTo(a.getRevenue()));
        vo.setCourseBreakdown(breakdown);

        // 5) 最近交易（最多 10 条）
        List<TeacherRevenueVO.RecentTransaction> recent = paidOrders.stream()
                .limit(10)
                .map(o -> {
                    TeacherRevenueVO.RecentTransaction t = new TeacherRevenueVO.RecentTransaction();
                    t.setOrderNo(o.getOrderNo());
                    t.setCourseTitle(courseTitleMap.getOrDefault(o.getCourseId(), "未知课程"));
                    t.setAmount(o.getAmount() != null ? o.getAmount() : java.math.BigDecimal.ZERO);
                    java.math.BigDecimal txPlatform = t.getAmount().multiply(platformRate)
                            .divide(java.math.BigDecimal.valueOf(100), java.math.RoundingMode.HALF_UP);
                    t.setPlatformShare(txPlatform);
                    t.setNetEarnings(t.getAmount().subtract(txPlatform));
                    t.setPaidAt(o.getPaidAt());
                    return t;
                })
                .collect(Collectors.toList());
        vo.setRecentTransactions(recent);

        return vo;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentActivityVO> getStudentActivity(Long teacherId, int days) {
        assertTeacherOwnership(teacherId, SecurityUtil.getCurrentUserId());
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
        assertTeacherOwnership(teacherId, SecurityUtil.getCurrentUserId());
        // ★ Round 11-2 性能核验：本方法已为批量预加载实现（无逐门课程 N+1）——
        // 1 次取教师课程 ID 集合 → 1 次 IN(courseIds) 批量取练习 ID → 1 次分页取待批改记录
        // → 1 次分页取讨论帖；查询次数恒定，不随课程数增长，无需引入 per-course 循环查询。
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
        assertTeacherOwnership(teacherId, SecurityUtil.getCurrentUserId());
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
        assertTeacherOwnership(teacherId, SecurityUtil.getCurrentUserId());
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

    // getPlatformShareRate 已被 PlatformShareRateResolver 取代 (修复 P0-1)

    /**
     * 校验教师归属：仅 ADMIN 或本人可访问（S-01 IDOR 防护）
     */
    private void assertTeacherOwnership(Long teacherId, Long currentUserId) {
        if (!SecurityUtil.isAdmin() && !teacherId.equals(currentUserId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "无权访问此教师的数据");
        }
    }
}