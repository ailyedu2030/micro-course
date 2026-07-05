package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.AcademicOverviewVO;
import com.microcourse.dto.CompletionWarningVO;
import com.microcourse.dto.CourseStatsVO;
import com.microcourse.dto.DepartmentDetailVO;
import com.microcourse.dto.DepartmentStatsVO;
import com.microcourse.dto.TrendPointVO;
import com.microcourse.entity.Course;
import com.microcourse.entity.Department;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.User;
import com.microcourse.enums.NotificationType;
import com.microcourse.enums.UserRole;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.DepartmentRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.ExerciseRecordRepository;
import com.microcourse.repository.TeachingClassRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.AcademicStatsService;
import com.microcourse.service.NotificationService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 教务处驾驶舱统计服务实现
 */
@Service
public class AcademicStatsServiceImpl implements AcademicStatsService {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final DepartmentRepository departmentRepository;
    private final ExerciseRecordRepository exerciseRecordRepository;
    private final TeachingClassRepository teachingClassRepository;
    private final NotificationService notificationService;

    public AcademicStatsServiceImpl(CourseRepository courseRepository,
                                    UserRepository userRepository,
                                    EnrollmentRepository enrollmentRepository,
                                    DepartmentRepository departmentRepository,
                                    ExerciseRecordRepository exerciseRecordRepository,
                                    TeachingClassRepository teachingClassRepository,
                                    NotificationService notificationService) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.departmentRepository = departmentRepository;
        this.exerciseRecordRepository = exerciseRecordRepository;
        this.teachingClassRepository = teachingClassRepository;
        this.notificationService = notificationService;
    }

    /**
     * C3 修复：教务处驾驶舱总览添加 Redis 缓存（TTL 5 分钟）。
     * 高频查询入口，缓存命中 ~3ms vs 回 DB ~150ms，大幅降低 DB 压力。
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "academicOverview", key = "'overview'", sync = true)
    public AcademicOverviewVO getOverview() {
        AcademicOverviewVO vo = new AcademicOverviewVO();

        // 总课程数（状态为已发布/已通过，status=2 即 APPROVED，或 status=4 PUBLISHED）
        // 按 spec: CourseStatus APPROVED=2
        long totalCourses = courseRepository.selectCount(
                new LambdaQueryWrapper<Course>()
                        .eq(Course::getStatus, 2)
                        .isNull(Course::getDeletedAt)
        );
        vo.setTotalCourses(totalCourses);

        // 总学生数
        long totalStudents = userRepository.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getRole, UserRole.STUDENT)
                        .isNull(User::getDeletedAt)
        );
        vo.setTotalStudents(totalStudents);

        // 总选课人次
        long totalEnrollments = enrollmentRepository.selectCount(
                new LambdaQueryWrapper<Enrollment>().isNull(Enrollment::getDeletedAt)
        );
        vo.setTotalEnrollments(totalEnrollments);

        // 平均完成率：从 enrollments 表计算 completed=true 的比例
        // 使用原生 SQL 聚合，避免子查询在 LambdaQueryWrapper 中的局限
        Double avgCompletion = courseRepository.selectAvgCompletionRate();
        vo.setAvgCompletionRate(avgCompletion != null ? avgCompletion : 0.0);

        // 平均正确率：从 exercise_records 表计算 avg(score/totalScore)
        Double avgAccuracy = exerciseRecordRepository.selectAvgAccuracyRate();
        vo.setAvgAccuracyRate(avgAccuracy != null ? avgAccuracy : 0.0);

        // 当前学期（取最新的 semester）
        String currentSemester = courseRepository.selectCurrentSemester();
        vo.setCurrentSemester(currentSemester != null ? currentSemester : "");

        vo.setUpdateAt(System.currentTimeMillis());

        return vo;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "academicOverview", key = "'deptStats'", sync = true)
    public List<DepartmentStatsVO> getDepartmentStats() {
        // 使用原生 SQL 按院系分组聚合统计
        List<Map<String, Object>> rawList = courseRepository.selectDepartmentStats();
        List<DepartmentStatsVO> result = new ArrayList<>();

        for (Map<String, Object> row : rawList) {
            DepartmentStatsVO vo = new DepartmentStatsVO();
            Object deptId = row.get("department_id");
            vo.setDepartmentId(deptId != null ? ((Number) deptId).longValue() : null);
            vo.setDepartmentName((String) row.get("department_name"));
            Object courseCount = row.get("course_count");
            vo.setCourseCount(courseCount != null ? ((Number) courseCount).longValue() : 0L);
            Object enrollmentCount = row.get("enrollment_count");
            vo.setEnrollmentCount(enrollmentCount != null ? ((Number) enrollmentCount).longValue() : 0L);
            Object studentCount = row.get("student_count");
            vo.setStudentCount(studentCount != null ? ((Number) studentCount).longValue() : 0L);
            Object avgComp = row.get("avg_completion_rate");
            vo.setAvgCompletionRate(avgComp != null ? ((Number) avgComp).doubleValue() : 0.0);
            Object avgAcc = row.get("avg_accuracy_rate");
            vo.setAvgAccuracyRate(avgAcc != null ? ((Number) avgAcc).doubleValue() : 0.0);
            result.add(vo);
        }

        // 按 avgCompletionRate 降序排名的 ranking
        final List<DepartmentStatsVO> sorted = result.stream()
                .sorted(Comparator.comparingDouble(DepartmentStatsVO::getAvgCompletionRate).reversed())
                .collect(Collectors.toList());
        for (int i = 0; i < sorted.size(); i++) {
            sorted.get(i).setRanking(i + 1);
        }

        return sorted;
    }

    @Override
    @Transactional(readOnly = true)
    public DepartmentDetailVO getDepartmentDetail(Long departmentId) {
        DepartmentDetailVO vo = new DepartmentDetailVO();

        // 查询院系信息
        Department dept = departmentRepository.selectById(departmentId);
        if (dept == null) {
            return vo;
        }
        vo.setDepartmentId(dept.getId());
        vo.setDepartmentName(dept.getName());

        // 查询该院系下所有课程（含统计）
        List<Map<String, Object>> rawCourses = courseRepository.selectCourseStatsByDepartment(departmentId);
        List<CourseStatsVO> courseStatsList = new ArrayList<>();

        for (Map<String, Object> row : rawCourses) {
            CourseStatsVO csv = new CourseStatsVO();
            Object courseId = row.get("course_id");
            csv.setCourseId(courseId != null ? ((Number) courseId).longValue() : null);
            csv.setCourseTitle((String) row.get("course_title"));
            csv.setTeacherName((String) row.get("teacher_name"));
            Object enrollCount = row.get("enrollment_count");
            csv.setEnrollmentCount(enrollCount != null ? ((Number) enrollCount).longValue() : 0L);
            Object compRate = row.get("completion_rate");
            csv.setCompletionRate(compRate != null ? ((Number) compRate).doubleValue() : 0.0);
            Object avgScore = row.get("avg_score");
            csv.setAvgScore(avgScore != null ? ((Number) avgScore).doubleValue() : 0.0);
            courseStatsList.add(csv);
        }
        vo.setCourses(courseStatsList);

        // 汇总字段
        vo.setTotalCourses((long) courseStatsList.size());
        long totalEnrollments = courseStatsList.stream()
                .mapToLong(CourseStatsVO::getEnrollmentCount).sum();
        vo.setTotalEnrollments(totalEnrollments);
        double avgComp = courseStatsList.stream()
                .mapToDouble(CourseStatsVO::getCompletionRate).average().orElse(0.0);
        vo.setAvgCompletionRate(avgComp);
        double avgAcc = courseStatsList.stream()
                .filter(c -> c.getAvgScore() != null && c.getAvgScore() > 0)
                .mapToDouble(CourseStatsVO::getAvgScore).average().orElse(0.0);
        vo.setAvgAccuracyRate(avgAcc);

        return vo;
    }

    /** P1I-061 修复：完成率预警阈值提取为可配置常量，与前端保持一致 */
    private static final double COMPLETION_WARNING_THRESHOLD = 30.0;   // 完成率低于此值为 warning
    private static final double COMPLETION_CRITICAL_THRESHOLD = 15.0;  // 完成率低于此值为 critical

    @Override
    @Transactional(readOnly = true)
    public List<CompletionWarningVO> getCompletionWarnings() {
        // 找出完成率 < 30% 的课程（阈值可配置）
        List<Map<String, Object>> rawList = courseRepository.selectCompletionWarnings();
        List<CompletionWarningVO> result = new ArrayList<>();

        for (Map<String, Object> row : rawList) {
            CompletionWarningVO vo = new CompletionWarningVO();
            Object courseId = row.get("course_id");
            vo.setCourseId(courseId != null ? ((Number) courseId).longValue() : null);
            vo.setCourseTitle((String) row.get("course_title"));
            vo.setTeacherName((String) row.get("teacher_name"));
            Object enrollCount = row.get("enrollment_count");
            vo.setEnrollmentCount(enrollCount != null ? ((Number) enrollCount).longValue() : 0L);
            Object compRate = row.get("completion_rate");
            double rate = compRate != null ? ((Number) compRate).doubleValue() : 0.0;
            vo.setCompletionRate(rate);
            // 完成率 < COMPLETION_CRITICAL_THRESHOLD 为 critical，其余为 warning
            vo.setStatus(rate < COMPLETION_CRITICAL_THRESHOLD ? "critical" : "warning");
            result.add(vo);
        }

        // P1C-064: 完成率预警主动推送 — 当存在预警课程时，通知所有 ACADEMIC 角色用户
        if (!result.isEmpty()) {
            List<User> academicUsers = userRepository.selectList(
                    new LambdaQueryWrapper<User>().eq(User::getRole, UserRole.ACADEMIC));
            List<CompletionWarningVO> criticalItems = result.stream()
                    .filter(w -> "critical".equals(w.getStatus())).collect(Collectors.toList());
            List<CompletionWarningVO> warningItems = result.stream()
                    .filter(w -> "warning".equals(w.getStatus())).collect(Collectors.toList());
            StringBuilder content = new StringBuilder();
            if (!criticalItems.isEmpty()) {
                content.append("【严重】").append(criticalItems.size()).append("门课程完成率低于15%：");
                for (CompletionWarningVO w : criticalItems) {
                    content.append("《").append(w.getCourseTitle()).append("》").append(String.format("%.1f%%", w.getCompletionRate())).append("；");
                }
            }
            if (!warningItems.isEmpty()) {
                if (content.length() > 0) content.append("\n");
                content.append("【提醒】").append(warningItems.size()).append("门课程完成率低于30%：");
                for (CompletionWarningVO w : warningItems) {
                    content.append("《").append(w.getCourseTitle()).append("》").append(String.format("%.1f%%", w.getCompletionRate())).append("；");
                }
            }
            for (User au : academicUsers) {
                notificationService.notifyAsync(au.getId(), NotificationType.COURSE_COMPLETION_WARNING,
                        "完成率预警", content.toString(), null);
            }
        }

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrendPointVO> getParticipationTrend(String semester) {
        List<Map<String, Object>> rawList = courseRepository.selectParticipationTrend(
                semesterToStartDate(semester), semesterToEndDate(semester));
        List<TrendPointVO> result = new ArrayList<>();

        for (Map<String, Object> row : rawList) {
            TrendPointVO vo = new TrendPointVO();
            vo.setMonth((String) row.get("month"));
            Object rate = row.get("participation_rate");
            vo.setValue(rate != null ? ((Number) rate).doubleValue() : 0.0);
            result.add(vo);
        }

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrendPointVO> getCompletionTrend(String semester) {
        List<Map<String, Object>> rawList = courseRepository.selectCompletionTrend(
                semesterToStartDate(semester), semesterToEndDate(semester));
        List<TrendPointVO> result = new ArrayList<>();

        for (Map<String, Object> row : rawList) {
            TrendPointVO vo = new TrendPointVO();
            vo.setMonth((String) row.get("month"));
            Object rate = row.get("completion_rate");
            vo.setValue(rate != null ? ((Number) rate).doubleValue() : 0.0);
            result.add(vo);
        }

        return result;
    }

    /**
     * 将学期编码转为起始日期字符串（参数化查询替代隐式类型转换）。
     * 学期格式: "2025-1"（上半年）或 "2025-2"（下半年）。
     * null 输入返回 null（不限定日期范围）。
     */
    private String semesterToStartDate(String semester) {
        if (semester == null || !semester.contains("-")) return null;
        String[] parts = semester.split("-");
        if (parts.length != 2) return null;
        String year = parts[0].trim();
        if ("1".equals(parts[1].trim())) return year + "-01-01";
        if ("2".equals(parts[1].trim())) return year + "-07-01";
        return null;
    }

    /**
     * 将学期编码转为结束日期字符串（配合 startDate 构成左闭右开区间）。
     * 上半年结束于 07-01，下半年结束于次年 01-01。
     */
    private String semesterToEndDate(String semester) {
        if (semester == null || !semester.contains("-")) return null;
        String[] parts = semester.split("-");
        if (parts.length != 2) return null;
        String year = parts[0].trim();
        if ("1".equals(parts[1].trim())) return year + "-07-01";
        if ("2".equals(parts[1].trim())) return (Integer.parseInt(year) + 1) + "-01-01";
        return null;
    }
}
