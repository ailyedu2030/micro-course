package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.microcourse.dto.CourseTrendVO;
import com.microcourse.dto.DailyActivityVO;
import com.microcourse.dto.DashboardOverviewVO;
import com.microcourse.dto.UserTrendVO;
import com.microcourse.entity.Certificate;
import com.microcourse.entity.Course;
import com.microcourse.entity.DiscussionPost;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.Exercise;
import com.microcourse.entity.LearningProgress;
import com.microcourse.entity.User;
import com.microcourse.entity.Video;
import com.microcourse.enums.CourseStatus;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.CertificateRepository;
import com.microcourse.repository.DiscussionPostRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.ExerciseRepository;
import com.microcourse.repository.LearningProgressRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.repository.VideoRepository;
import com.microcourse.service.AdminStatsService;
import com.microcourse.util.RedisUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台数据统计服务实现
 */
@Service
public class AdminStatsServiceImpl implements AdminStatsService {

    private static final Logger log = LoggerFactory.getLogger(AdminStatsServiceImpl.class);

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final VideoRepository videoRepository;
    private final ExerciseRepository exerciseRepository;
    private final DiscussionPostRepository discussionPostRepository;
    private final LearningProgressRepository learningProgressRepository;
    private final CertificateRepository certificateRepository;
    private final RedisUtil redisUtil;
    private final DataSource dataSource;

    public AdminStatsServiceImpl(UserRepository userRepository,
                                  CourseRepository courseRepository,
                                  EnrollmentRepository enrollmentRepository,
                                  VideoRepository videoRepository,
                                  ExerciseRepository exerciseRepository,
                                  DiscussionPostRepository discussionPostRepository,
                                  LearningProgressRepository learningProgressRepository,
                                  CertificateRepository certificateRepository,
                                  RedisUtil redisUtil,
                                  DataSource dataSource) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.videoRepository = videoRepository;
        this.exerciseRepository = exerciseRepository;
        this.discussionPostRepository = discussionPostRepository;
        this.learningProgressRepository = learningProgressRepository;
        this.certificateRepository = certificateRepository;
        this.redisUtil = redisUtil;
        this.dataSource = dataSource;
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardOverviewVO getOverview() {
        DashboardOverviewVO vo = new DashboardOverviewVO();

        // 总用户数
        vo.setTotalUsers(userRepository.selectCount(new LambdaQueryWrapper<User>().isNull(User::getDeletedAt)));

        // 7日内活跃用户数（last_login_at 在 7 天内）
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        vo.setActiveUsers7d(userRepository.selectCount(
                new LambdaQueryWrapper<User>()
                        .isNull(User::getDeletedAt)
                        .ge(User::getLastLoginAt, sevenDaysAgo)
        ));

        // 总课程数
        vo.setTotalCourses(courseRepository.selectCount(null));

        // 已发布课程数（status = 4）
        vo.setPublishedCourses(courseRepository.selectCount(
                new LambdaQueryWrapper<Course>().eq(Course::getStatus, CourseStatus.PUBLISHED.getCode())
        ));

        // 总选课数
        vo.setTotalEnrollments(enrollmentRepository.selectCount(null));

        // 总视频数
        vo.setTotalVideos(videoRepository.selectCount(null));

        // 总练习数
        vo.setTotalExercises(exerciseRepository.selectCount(null));

        // 总讨论帖数
        vo.setTotalDiscussions(discussionPostRepository.selectCount(null));

        // 总观看时长（分钟）从 learning_progress.total_watch_time 求和（SQL聚合，避免OOM）
        Long totalWatchTimeSeconds = learningProgressRepository.sumTotalWatchTime();
        vo.setTotalWatchTimeMinutes(totalWatchTimeSeconds / 60);

        // 证书发放总数
        vo.setCertificatesIssued(certificateRepository.selectCount(null));

        return vo;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserTrendVO> getUserTrend(int days) {
        List<UserTrendVO> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // 用 GROUP BY 替代 N 次查询,一次性获取 days 内每日新增用户数
        LocalDateTime start = today.minusDays(days - 1).atStartOfDay();
        List<java.util.Map<String, Object>> createdRows = userRepository.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>()
                        .ge("created_at", start)
                        .isNull("deleted_at")
                        .select("DATE(created_at) as day, COUNT(*) as cnt")
                        .groupBy("day")
        );
        java.util.Map<String, Long> createdMap = new java.util.HashMap<>();
        for (java.util.Map<String, Object> row : createdRows) {
            createdMap.put(String.valueOf(row.get("day")), ((Number) row.get("cnt")).longValue());
        }

        // 活跃用户
        List<java.util.Map<String, Object>> activeRows = userRepository.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>()
                        .ge("last_login_at", start)
                        .isNull("deleted_at")
                        .select("DATE(last_login_at) as day, COUNT(*) as cnt")
                        .groupBy("day")
        );
        java.util.Map<String, Long> activeMap = new java.util.HashMap<>();
        for (java.util.Map<String, Object> row : activeRows) {
            activeMap.put(String.valueOf(row.get("day")), ((Number) row.get("cnt")).longValue());
        }

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            String key = date.format(formatter);
            result.add(new UserTrendVO(key,
                    createdMap.getOrDefault(key, 0L),
                    activeMap.getOrDefault(key, 0L)));
        }

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseTrendVO> getCourseTrend(int days) {
        List<CourseTrendVO> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // 用 GROUP BY 替代 N 次查询
        LocalDateTime start = today.minusDays(days - 1).atStartOfDay();
        List<java.util.Map<String, Object>> courseRows = courseRepository.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Course>()
                        .ge("created_at", start)
                        .select("DATE(created_at) as day, COUNT(*) as cnt")
                        .groupBy("day")
        );
        java.util.Map<String, Long> courseMap = new java.util.HashMap<>();
        for (java.util.Map<String, Object> row : courseRows) {
            courseMap.put(String.valueOf(row.get("day")), ((Number) row.get("cnt")).longValue());
        }

        List<java.util.Map<String, Object>> enrollRows = enrollmentRepository.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Enrollment>()
                        .ge("enrolled_at", start)
                        .select("DATE(enrolled_at) as day, COUNT(*) as cnt")
                        .groupBy("day")
        );
        java.util.Map<String, Long> enrollMap = new java.util.HashMap<>();
        for (java.util.Map<String, Object> row : enrollRows) {
            enrollMap.put(String.valueOf(row.get("day")), ((Number) row.get("cnt")).longValue());
        }

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            String key = date.format(formatter);
            result.add(new CourseTrendVO(key,
                    courseMap.getOrDefault(key, 0L),
                    enrollMap.getOrDefault(key, 0L)));
        }

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCourseDistribution() {
        // RES-006 修复:用单次 GROUP BY 替代每状态一次 selectCount
        List<Map<String, Object>> grouped = courseRepository.selectMaps(
                new QueryWrapper<Course>()
                        .select("status", "COUNT(*) AS cnt")
                        .groupBy("status")
        );
        Map<Integer, Long> countByStatus = new HashMap<>();
        for (Map<String, Object> row : grouped) {
            Object statusVal = row.get("status");
            Object cntVal = row.get("cnt");
            Integer key = statusVal == null ? null : (statusVal instanceof Number n ? n.intValue() : Integer.parseInt(statusVal.toString()));
            Long cnt = cntVal == null ? 0L : (cntVal instanceof Number n ? n.longValue() : Long.parseLong(cntVal.toString()));
            if (key != null) countByStatus.put(key, cnt);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (CourseStatus status : CourseStatus.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("status", status.name());
            item.put("count", countByStatus.getOrDefault(status.getCode(), 0L));
            result.add(item);
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getLearningBehavior() {
        List<Map<String, Object>> result = new ArrayList<>();

        // VIDEO_WATCH - count learning_progress records (video watches)
        Long videoWatchCount = learningProgressRepository.selectCount(null);
        Map<String, Object> videoWatch = new LinkedHashMap<>();
        videoWatch.put("type", "VIDEO_WATCH");
        videoWatch.put("count", videoWatchCount);
        result.add(videoWatch);

        // EXERCISE_SUBMIT - count where exercise_completed = true
        Long exerciseSubmitCount = learningProgressRepository.selectCount(
                new LambdaQueryWrapper<LearningProgress>()
                        .eq(LearningProgress::getExerciseCompleted, true)
        );
        Map<String, Object> exerciseSubmit = new LinkedHashMap<>();
        exerciseSubmit.put("type", "EXERCISE_SUBMIT");
        exerciseSubmit.put("count", exerciseSubmitCount);
        result.add(exerciseSubmit);

        // COMPLETED - count where completed = true
        Long completedCount = learningProgressRepository.selectCount(
                new LambdaQueryWrapper<LearningProgress>()
                        .eq(LearningProgress::getCompleted, true)
        );
        Map<String, Object> completed = new LinkedHashMap<>();
        completed.put("type", "COURSE_COMPLETED");
        completed.put("count", completedCount);
        result.add(completed);

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyActivityVO> getDailyActivity(int days) {
        List<DailyActivityVO> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        LocalDateTime start = today.minusDays(days - 1).atStartOfDay();
        List<Map<String, Object>> rows = userRepository.selectMaps(
                new QueryWrapper<User>()
                        .ge("last_login_at", start)
                        .isNull("deleted_at")
                        .select("DATE(last_login_at) as day, COUNT(*) as cnt")
                        .groupBy("day")
        );
        Map<String, Long> activeMap = new HashMap<>();
        for (Map<String, Object> row : rows) {
            activeMap.put(String.valueOf(row.get("day")), ((Number) row.get("cnt")).longValue());
        }

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            String key = date.format(formatter);
            result.add(new DailyActivityVO(key, activeMap.getOrDefault(key, 0L)));
        }

        return result;
    }

    @Override
    public Map<String, String> getHealth() {
        Map<String, String> health = new LinkedHashMap<>();

        // DB check
        try (Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(2);
            health.put("db", valid ? "OK" : "ERROR");
        } catch (Exception e) {
            log.warn("[Health] DB check failed", e);
            health.put("db", "ERROR");
        }

        // Redis check
        try {
            String pong = redisUtil.ping();
            health.put("redis", "PONG".equals(pong) ? "OK" : "ERROR");
        } catch (Exception e) {
            log.warn("[Health] Redis check failed", e);
            health.put("redis", "ERROR");
        }

        // Disk check
        try {
            java.io.File root = new java.io.File("/");
            long total = root.getTotalSpace();
            long free = root.getFreeSpace();
            long used = total - free;
            double usedPercent = (used * 100.0) / total;
            health.put("disk", usedPercent > 90 ? "WARN" : "OK");
        } catch (Exception e) {
            health.put("disk", "UNKNOWN");
        }

        // Memory check
        try {
            Runtime rt = Runtime.getRuntime();
            long totalMem = rt.totalMemory();
            long freeMem = rt.freeMemory();
            long usedMem = totalMem - freeMem;
            double usedPercent = (usedMem * 100.0) / totalMem;
            health.put("memory", usedPercent > 90 ? "WARN" : "OK");
        } catch (Exception e) {
            health.put("memory", "UNKNOWN");
        }

        return health;
    }
}