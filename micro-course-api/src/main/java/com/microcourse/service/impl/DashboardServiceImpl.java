package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.DashboardActivityVO;
import com.microcourse.dto.DashboardOverviewVO;
import com.microcourse.dto.DashboardProgressVO;
import com.microcourse.dto.DashboardRevenueVO;
import com.microcourse.entity.*;
import com.microcourse.enums.CourseStatus;
import com.microcourse.enums.EnrollmentStatus;
import com.microcourse.repository.*;
import com.microcourse.service.DashboardService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardServiceImpl implements DashboardService {

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final VideoRepository videoRepository;
    private final ExerciseRepository exerciseRepository;
    private final DiscussionPostRepository discussionPostRepository;
    private final LearningProgressRepository learningProgressRepository;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OperationLogRepository operationLogRepository;
    private final CertificateRepository certificateRepository;

    public DashboardServiceImpl(UserRepository userRepository,
                                CourseRepository courseRepository,
                                EnrollmentRepository enrollmentRepository,
                                VideoRepository videoRepository,
                                ExerciseRepository exerciseRepository,
                                DiscussionPostRepository discussionPostRepository,
                                LearningProgressRepository learningProgressRepository,
                                PaymentRepository paymentRepository,
                                OrderRepository orderRepository,
                                OperationLogRepository operationLogRepository,
                                CertificateRepository certificateRepository) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.videoRepository = videoRepository;
        this.exerciseRepository = exerciseRepository;
        this.discussionPostRepository = discussionPostRepository;
        this.learningProgressRepository = learningProgressRepository;
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.operationLogRepository = operationLogRepository;
        this.certificateRepository = certificateRepository;
    }

    @Override
    public DashboardOverviewVO getOverview() {
        DashboardOverviewVO vo = new DashboardOverviewVO();

        long totalUsers = userRepository.selectCount(null);
        vo.setTotalUsers(totalUsers);

        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        LambdaQueryWrapper<User> activeUsersWrapper = new LambdaQueryWrapper<>();
        activeUsersWrapper.gt(User::getUpdatedAt, sevenDaysAgo);
        long activeUsers7d = userRepository.selectCount(activeUsersWrapper);
        vo.setActiveUsers7d(activeUsers7d);

        long totalCourses = courseRepository.selectCount(null);
        vo.setTotalCourses(totalCourses);

        LambdaQueryWrapper<Course> publishedWrapper = new LambdaQueryWrapper<>();
        publishedWrapper.eq(Course::getStatus, CourseStatus.PUBLISHED.getCode());
        long publishedCourses = courseRepository.selectCount(publishedWrapper);
        vo.setPublishedCourses(publishedCourses);

        long totalEnrollments = enrollmentRepository.selectCount(null);
        vo.setTotalEnrollments(totalEnrollments);

        long totalVideos = videoRepository.selectCount(null);
        vo.setTotalVideos(totalVideos);

        long totalExercises = exerciseRepository.selectCount(null);
        vo.setTotalExercises(totalExercises);

        long totalDiscussions = discussionPostRepository.selectCount(null);
        vo.setTotalDiscussions(totalDiscussions);

        LambdaQueryWrapper<LearningProgress> progressWrapper = new LambdaQueryWrapper<>();
        progressWrapper.select(com.microcourse.entity.LearningProgress::getTotalWatchTime);
        List<LearningProgress> progressList = learningProgressRepository.selectList(progressWrapper);
        long totalWatchTimeMinutes = progressList.stream()
                .filter(p -> p.getTotalWatchTime() != null)
                .mapToLong(p -> p.getTotalWatchTime() / 60)
                .sum();
        vo.setTotalWatchTimeMinutes(totalWatchTimeMinutes);

        long certificatesIssued = certificateRepository.selectCount(null);
        vo.setCertificatesIssued(certificatesIssued);

        return vo;
    }

    @Override
    public DashboardProgressVO getProgress() {
        DashboardProgressVO vo = new DashboardProgressVO();

        LambdaQueryWrapper<Enrollment> enrollmentWrapper = new LambdaQueryWrapper<>();
        enrollmentWrapper.in(Enrollment::getEnrollmentStatus,
                EnrollmentStatus.LEGACY_ENROLLED_VALUE, EnrollmentStatus.APPROVED.getValue());
        long totalStudents = enrollmentRepository.selectCount(enrollmentWrapper);
        vo.setTotalStudents(totalStudents);

        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        LambdaQueryWrapper<LearningProgress> activeWrapper = new LambdaQueryWrapper<>();
        activeWrapper.gt(LearningProgress::getLastWatchAt, sevenDaysAgo);
        activeWrapper.select(LearningProgress::getUserId);
        List<LearningProgress> activeList = learningProgressRepository.selectList(activeWrapper);
        long activeStudents = activeList.stream()
                .filter(p -> p.getUserId() != null)
                .map(LearningProgress::getUserId)
                .distinct()
                .count();
        vo.setActiveStudents(activeStudents);

        LambdaQueryWrapper<Enrollment> completedWrapper = new LambdaQueryWrapper<>();
        completedWrapper.eq(Enrollment::getCompleted, true);
        long completedStudents = enrollmentRepository.selectCount(completedWrapper);
        vo.setCompletedStudents(completedStudents);

        if (totalStudents > 0) {
            BigDecimal completionRate = BigDecimal.valueOf(completedStudents)
                    .divide(BigDecimal.valueOf(totalStudents), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            vo.setCompletionRate(completionRate);
        } else {
            vo.setCompletionRate(BigDecimal.ZERO);
        }

        LambdaQueryWrapper<Enrollment> progressWrapper = new LambdaQueryWrapper<>();
        progressWrapper.select(Enrollment::getProgress);
        List<Enrollment> enrollments = enrollmentRepository.selectList(progressWrapper);
        double avgProgress = enrollments.stream()
                .filter(e -> e.getProgress() != null)
                .mapToDouble(Enrollment::getProgress)
                .average()
                .orElse(0.0);
        vo.setAvgProgress(BigDecimal.valueOf(avgProgress).setScale(2, RoundingMode.HALF_UP));

        LambdaQueryWrapper<LearningProgress> timeWrapper = new LambdaQueryWrapper<>();
        timeWrapper.select(com.microcourse.entity.LearningProgress::getTotalWatchTime);
        List<LearningProgress> timeList = learningProgressRepository.selectList(timeWrapper);
        long totalLearningMinutes = timeList.stream()
                .filter(p -> p.getTotalWatchTime() != null)
                .mapToLong(p -> p.getTotalWatchTime() / 60)
                .sum();
        vo.setTotalLearningMinutes(totalLearningMinutes);

        return vo;
    }

    @Override
    public DashboardActivityVO getActivity() {
        DashboardActivityVO vo = new DashboardActivityVO();

        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime weekStart = todayStart.minusDays(7);
        LocalDateTime monthStart = todayStart.minusDays(30);

        LambdaQueryWrapper<OperationLog> dailyWrapper = new LambdaQueryWrapper<>();
        dailyWrapper.gt(OperationLog::getCreatedAt, todayStart);
        dailyWrapper.likeRight(OperationLog::getAction, "LOGIN");
        long dailyActive = operationLogRepository.selectCount(dailyWrapper);
        vo.setDailyActiveUsers(dailyActive);

        LambdaQueryWrapper<OperationLog> weeklyWrapper = new LambdaQueryWrapper<>();
        weeklyWrapper.gt(OperationLog::getCreatedAt, weekStart);
        weeklyWrapper.likeRight(OperationLog::getAction, "LOGIN");
        weeklyWrapper.select(OperationLog::getUserId);
        List<OperationLog> weeklyLogs = operationLogRepository.selectList(weeklyWrapper);
        long weeklyActive = weeklyLogs.stream()
                .filter(l -> l.getUserId() != null)
                .map(OperationLog::getUserId)
                .distinct()
                .count();
        vo.setWeeklyActiveUsers(weeklyActive);

        LambdaQueryWrapper<OperationLog> monthlyWrapper = new LambdaQueryWrapper<>();
        monthlyWrapper.gt(OperationLog::getCreatedAt, monthStart);
        monthlyWrapper.likeRight(OperationLog::getAction, "LOGIN");
        monthlyWrapper.select(OperationLog::getUserId);
        List<OperationLog> monthlyLogs = operationLogRepository.selectList(monthlyWrapper);
        long monthlyActive = monthlyLogs.stream()
                .filter(l -> l.getUserId() != null)
                .map(OperationLog::getUserId)
                .distinct()
                .count();
        vo.setMonthlyActiveUsers(monthlyActive);

        LambdaQueryWrapper<OperationLog> loginWrapper = new LambdaQueryWrapper<>();
        loginWrapper.likeRight(OperationLog::getAction, "LOGIN");
        long totalLogins = operationLogRepository.selectCount(loginWrapper);
        vo.setTotalLogins(totalLogins);

        List<Map<String, Object>> dailyTrend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            LocalDateTime dayStart = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime dayEnd = dayStart.plusDays(1);

            LambdaQueryWrapper<OperationLog> dayWrapper = new LambdaQueryWrapper<>();
            dayWrapper.gt(OperationLog::getCreatedAt, dayStart);
            dayWrapper.lt(OperationLog::getCreatedAt, dayEnd);
            dayWrapper.likeRight(OperationLog::getAction, "LOGIN");
            long count = operationLogRepository.selectCount(dayWrapper);

            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", date.toString());
            dayData.put("count", count);
            dailyTrend.add(dayData);
        }
        vo.setDailyTrend(dailyTrend);

        LambdaQueryWrapper<OperationLog> allLogsWrapper = new LambdaQueryWrapper<>();
        allLogsWrapper.gt(OperationLog::getCreatedAt, weekStart);
        allLogsWrapper.likeRight(OperationLog::getAction, "LOGIN");
        allLogsWrapper.select(OperationLog::getUserId);
        List<OperationLog> allLogs = operationLogRepository.selectList(allLogsWrapper);

        Map<Long, Long> userLoginCount = allLogs.stream()
                .filter(l -> l.getUserId() != null)
                .collect(Collectors.groupingBy(OperationLog::getUserId, Collectors.counting()));

        List<Map<String, Object>> topStudents = new ArrayList<>();
        userLoginCount.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(10)
                .forEach(entry -> {
                    Map<String, Object> studentData = new HashMap<>();
                    studentData.put("userId", entry.getKey());
                    User user = userRepository.selectById(entry.getKey());
                    studentData.put("userName", user != null ? user.getRealName() : "未知");
                    topStudents.add(studentData);
                });
        vo.setTopActiveStudents(topStudents);

        return vo;
    }

    @Override
    public DashboardRevenueVO getRevenue() {
        DashboardRevenueVO vo = new DashboardRevenueVO();

        LambdaQueryWrapper<Payment> paymentWrapper = new LambdaQueryWrapper<>();
        paymentWrapper.eq(Payment::getStatus, "COMPLETED");
        paymentWrapper.select(Payment::getAmount);
        List<Payment> payments = paymentRepository.selectList(paymentWrapper);

        BigDecimal totalRevenue = payments.stream()
                .filter(p -> p.getAmount() != null)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setTotalRevenue(totalRevenue);

        LocalDateTime monthStart = LocalDateTime.now().minusDays(30);
        LambdaQueryWrapper<Payment> monthlyWrapper = new LambdaQueryWrapper<>();
        monthlyWrapper.eq(Payment::getStatus, "COMPLETED");
        monthlyWrapper.gt(Payment::getCreatedAt, monthStart);
        monthlyWrapper.select(Payment::getAmount);
        List<Payment> monthlyPayments = paymentRepository.selectList(monthlyWrapper);
        BigDecimal monthlyRevenue = monthlyPayments.stream()
                .filter(p -> p.getAmount() != null)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setMonthlyRevenue(monthlyRevenue);

        LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<>();
        long totalOrders = orderRepository.selectCount(orderWrapper);
        vo.setTotalOrders(totalOrders);

        LambdaQueryWrapper<Order> paidWrapper = new LambdaQueryWrapper<>();
        paidWrapper.eq(Order::getStatus, "PAID");
        long paidOrders = orderRepository.selectCount(paidWrapper);
        vo.setPaidOrders(paidOrders);

        if (paidOrders > 0) {
            BigDecimal avgAmount = totalRevenue.divide(BigDecimal.valueOf(paidOrders), 2, RoundingMode.HALF_UP);
            vo.setAvgOrderAmount(avgAmount);
        } else {
            vo.setAvgOrderAmount(BigDecimal.ZERO);
        }

        List<Map<String, Object>> dailyTrend = new ArrayList<>();
        for (int i = 29; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            LocalDateTime dayStart = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime dayEnd = dayStart.plusDays(1);

            LambdaQueryWrapper<Payment> dayWrapper = new LambdaQueryWrapper<>();
            dayWrapper.eq(Payment::getStatus, "COMPLETED");
            dayWrapper.gt(Payment::getCreatedAt, dayStart);
            dayWrapper.lt(Payment::getCreatedAt, dayEnd);
            dayWrapper.select(Payment::getAmount);
            List<Payment> dayPayments = paymentRepository.selectList(dayWrapper);
            BigDecimal dayRevenue = dayPayments.stream()
                    .filter(p -> p.getAmount() != null)
                    .map(Payment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", date.toString());
            dayData.put("revenue", dayRevenue);
            dailyTrend.add(dayData);
        }
        vo.setDailyTrend(dailyTrend);

        LambdaQueryWrapper<Order> paidOrdersWrapper = new LambdaQueryWrapper<>();
        paidOrdersWrapper.eq(Order::getStatus, "PAID");
        paidOrdersWrapper.last("ORDER BY created_at DESC LIMIT 10");
        List<Order> recentOrders = orderRepository.selectList(paidOrdersWrapper);

        Map<Long, BigDecimal> courseRevenueMap = new HashMap<>();
        for (Order order : recentOrders) {
            if (order.getCourseId() != null && order.getAmount() != null) {
                courseRevenueMap.merge(order.getCourseId(), order.getAmount(), BigDecimal::add);
            }
        }

        List<Map<String, Object>> topCourses = new ArrayList<>();
        if (!courseRevenueMap.isEmpty()) {
            Set<Long> courseIds = courseRevenueMap.keySet();
            Map<Long, Course> courseMap = new HashMap<>();
            if (!courseIds.isEmpty()) {
                courseRepository.selectBatchIds(courseIds).forEach(c -> courseMap.put(c.getId(), c));
            }

            courseRevenueMap.entrySet().stream()
                    .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                    .limit(10)
                    .forEach(entry -> {
                        Map<String, Object> courseData = new HashMap<>();
                        Course course = courseMap.get(entry.getKey());
                        courseData.put("courseId", entry.getKey());
                        courseData.put("courseTitle", course != null ? course.getTitle() : "未知课程");
                        courseData.put("revenue", entry.getValue());
                        topCourses.add(courseData);
                    });
        }
        vo.setTopCourses(topCourses);

        return vo;
    }
}