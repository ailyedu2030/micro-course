package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.CourseTrendVO;
import com.microcourse.dto.DashboardOverviewVO;
import com.microcourse.dto.UserTrendVO;
import com.microcourse.entity.Course;
import com.microcourse.entity.DiscussionPost;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.Exercise;
import com.microcourse.entity.LearningProgress;
import com.microcourse.entity.User;
import com.microcourse.entity.Video;
import com.microcourse.enums.CourseStatus;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.DiscussionPostRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.ExerciseRepository;
import com.microcourse.repository.LearningProgressRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.repository.VideoRepository;
import com.microcourse.service.AdminStatsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台数据统计服务实现
 */
@Service
public class AdminStatsServiceImpl implements AdminStatsService {

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final VideoRepository videoRepository;
    private final ExerciseRepository exerciseRepository;
    private final DiscussionPostRepository discussionPostRepository;
    private final LearningProgressRepository learningProgressRepository;

    public AdminStatsServiceImpl(UserRepository userRepository,
                                  CourseRepository courseRepository,
                                  EnrollmentRepository enrollmentRepository,
                                  VideoRepository videoRepository,
                                  ExerciseRepository exerciseRepository,
                                  DiscussionPostRepository discussionPostRepository,
                                  LearningProgressRepository learningProgressRepository) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.videoRepository = videoRepository;
        this.exerciseRepository = exerciseRepository;
        this.discussionPostRepository = discussionPostRepository;
        this.learningProgressRepository = learningProgressRepository;
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

        return vo;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserTrendVO> getUserTrend(int days) {
        List<UserTrendVO> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime startOfDay = date.atStartOfDay();
            LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

            // 当日新增用户
            Long newUsers = userRepository.selectCount(
                    new LambdaQueryWrapper<User>()
                            .ge(User::getCreatedAt, startOfDay)
                            .le(User::getCreatedAt, endOfDay)
                            .isNull(User::getDeletedAt)
            );

            // 当日活跃用户（当日有 last_login 记录）
            Long activeUsers = userRepository.selectCount(
                    new LambdaQueryWrapper<User>()
                            .ge(User::getLastLoginAt, startOfDay)
                            .le(User::getLastLoginAt, endOfDay)
                            .isNull(User::getDeletedAt)
            );

            result.add(new UserTrendVO(date.format(formatter), newUsers, activeUsers));
        }

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseTrendVO> getCourseTrend(int days) {
        List<CourseTrendVO> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime startOfDay = date.atStartOfDay();
            LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

            // 当日新增课程
            Long newCourses = courseRepository.selectCount(
                    new LambdaQueryWrapper<Course>()
                            .ge(Course::getCreatedAt, startOfDay)
                            .le(Course::getCreatedAt, endOfDay)
            );

            // 当日选课人数（enrolled_at 在当日）
            Long enrollments = enrollmentRepository.selectCount(
                    new LambdaQueryWrapper<Enrollment>()
                            .ge(Enrollment::getEnrolledAt, startOfDay)
                            .le(Enrollment::getEnrolledAt, endOfDay)
            );

            result.add(new CourseTrendVO(date.format(formatter), newCourses, enrollments));
        }

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCourseDistribution() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (CourseStatus status : CourseStatus.values()) {
            Long count = courseRepository.selectCount(
                    new LambdaQueryWrapper<Course>().eq(Course::getStatus, status.getCode())
            );
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("status", status.name());
            item.put("count", count);
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
}