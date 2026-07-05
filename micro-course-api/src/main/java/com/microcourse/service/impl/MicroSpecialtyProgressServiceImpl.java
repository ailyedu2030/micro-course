package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.microcourse.entity.Course;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.MicroSpecialty;
import com.microcourse.entity.MicroSpecialtyCourse;
import com.microcourse.entity.MicroSpecialtyEnrollment;
import com.microcourse.enums.EnrollmentStatus;
import com.microcourse.enums.NotificationType;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.MicroSpecialtyCourseRepository;
import com.microcourse.repository.MicroSpecialtyEnrollmentRepository;
import com.microcourse.repository.MicroSpecialtyRepository;
import com.microcourse.service.MicroSpecialtyEnrollmentService;
import com.microcourse.service.MicroSpecialtyProgressService;
import com.microcourse.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class MicroSpecialtyProgressServiceImpl implements MicroSpecialtyProgressService {

    private static final Logger log = LoggerFactory.getLogger(MicroSpecialtyProgressServiceImpl.class);

    // ====== 选课状态常量 ======
    private static final String ENROLLMENT_STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String ENROLLMENT_STATUS_APPROVED = "APPROVED";
    private static final String ENROLLMENT_STATUS_COMPLETED = "COMPLETED";
    private static final String ENROLLMENT_STATUS_FAILED = "FAILED";

    // ====== 最终成绩等级常量 ======
    private static final String FINAL_GRADE_EXCELLENT = "EXCELLENT";
    private static final String FINAL_GRADE_GOOD = "GOOD";
    private static final String FINAL_GRADE_PASS = "PASS";
    private static final String FINAL_GRADE_FAIL = "FAIL";

    // ====== 完成规则常量 ======
    private static final String COMPLETION_RULE_ALL_REQUIRED = "ALL_REQUIRED";
    private static final String COMPLETION_RULE_CREDITS_MIN = "CREDITS_MIN";
    private static final String COMPLETION_RULE_MIXED = "MIXED";

    private final MicroSpecialtyEnrollmentRepository enrollmentRepository;
    private final MicroSpecialtyRepository msRepository;
    private final MicroSpecialtyCourseRepository msCourseRepository;
    private final EnrollmentRepository courseEnrollmentRepository;
    private final CourseRepository courseRepository;
    private final NotificationService notificationService;
    private final MicroSpecialtyEnrollmentService enrollmentService;

    public MicroSpecialtyProgressServiceImpl(MicroSpecialtyEnrollmentRepository enrollmentRepository,
                                             MicroSpecialtyRepository msRepository,
                                             MicroSpecialtyCourseRepository msCourseRepository,
                                             EnrollmentRepository courseEnrollmentRepository,
                                             CourseRepository courseRepository,
                                             NotificationService notificationService,
                                             @Lazy MicroSpecialtyEnrollmentService enrollmentService) {
        this.enrollmentRepository = enrollmentRepository;
        this.msRepository = msRepository;
        this.msCourseRepository = msCourseRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.courseRepository = courseRepository;
        this.notificationService = notificationService;
        this.enrollmentService = enrollmentService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void aggregateProgress(Long enrollmentId) {
        MicroSpecialtyEnrollment en = enrollmentRepository.selectById(enrollmentId);
        if (en == null) return;

        if (!ENROLLMENT_STATUS_IN_PROGRESS.equals(en.getStatus()) && !ENROLLMENT_STATUS_APPROVED.equals(en.getStatus())) return;

        MicroSpecialty ms = msRepository.selectById(en.getMicroSpecialtyId());
        if (ms == null) return;

        // 获取所有必修课
        List<MicroSpecialtyCourse> requiredCourses = msCourseRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialtyCourse>()
                        .eq(MicroSpecialtyCourse::getMicroSpecialtyId, en.getMicroSpecialtyId())
                        .eq(MicroSpecialtyCourse::getIsRequired, true));

        // P0-11: 获取所有选修课
        List<MicroSpecialtyCourse> electiveCourses = msCourseRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialtyCourse>()
                        .eq(MicroSpecialtyCourse::getMicroSpecialtyId, en.getMicroSpecialtyId())
                        .eq(MicroSpecialtyCourse::getIsRequired, false));

        int coursesCompleted = 0;
        double totalRequiredScore = 0;
        int requiredCount = 0;
        double totalElectiveScore = 0;
        int electivePassedCount = 0;
        BigDecimal creditsEarned = BigDecimal.ZERO;

        // P1-11: progress = AVG(每门必修课 enrollment.progress × 100)
        double totalProgress = 0;
        int progressCount = 0;

        // P0-5: FAILED 判定数据
        boolean allCoursesGraded = !requiredCourses.isEmpty();
        boolean anyPassed = false;
        List<String> failedCourseNames = new ArrayList<>();

        // 预加载课程名称（用于 FAILED 通知）
        Set<Long> allCourseIds = new HashSet<>();
        for (MicroSpecialtyCourse mc : requiredCourses) allCourseIds.add(mc.getCourseId());
        for (MicroSpecialtyCourse mc : electiveCourses) allCourseIds.add(mc.getCourseId());
        Map<Long, String> courseNameMap = new HashMap<>();
        if (!allCourseIds.isEmpty()) {
            List<Course> courses = courseRepository.selectBatchIds(allCourseIds);
            for (Course c : courses) courseNameMap.put(c.getId(), c.getTitle());
        }

        // 必修课处理
        for (MicroSpecialtyCourse mc : requiredCourses) {
            Enrollment courseEn = courseEnrollmentRepository.selectOne(
                    new LambdaQueryWrapper<Enrollment>()
                            .eq(Enrollment::getCourseId, mc.getCourseId())
                            .eq(Enrollment::getUserId, en.getUserId())
                            .ne(Enrollment::getEnrollmentStatus, EnrollmentStatus.CANCELLED.getValue()));
            if (courseEn != null) {
                // P1-11: 收集课程级 progress
                if (courseEn.getProgress() != null) {
                    totalProgress += courseEn.getProgress() * 100.0;
                    progressCount++;
                }
                if (courseEn.getFinalScore() != null) {
                    BigDecimal minScore = mc.getMinScore() != null ? mc.getMinScore() : BigDecimal.valueOf(60);
                    boolean passed = courseEn.getFinalScore().compareTo(minScore) >= 0;
                    if (passed) {
                        coursesCompleted++;
                        creditsEarned = creditsEarned.add(mc.getCredits() != null ? mc.getCredits() : BigDecimal.ZERO);
                        anyPassed = true;
                    } else {
                        String name = courseNameMap.getOrDefault(mc.getCourseId(), "课程#" + mc.getCourseId());
                        failedCourseNames.add(name);
                    }
                    totalRequiredScore += courseEn.getFinalScore().doubleValue();
                    requiredCount++;
                }
            } else {
                // §9.10: 无 active enrollment 时，检查历史选课中是否有已通过成绩
                Enrollment historyEn = courseEnrollmentRepository.selectOne(
                        new LambdaQueryWrapper<Enrollment>()
                                .eq(Enrollment::getCourseId, mc.getCourseId())
                                .eq(Enrollment::getUserId, en.getUserId()));
                if (historyEn != null && historyEn.getFinalScore() != null) {
                    BigDecimal minScore = mc.getMinScore() != null ? mc.getMinScore() : BigDecimal.valueOf(60);
                    boolean passed = historyEn.getFinalScore().compareTo(minScore) >= 0;
                    if (passed) {
                        coursesCompleted++;
                        creditsEarned = creditsEarned.add(mc.getCredits() != null ? mc.getCredits() : BigDecimal.ZERO);
                        anyPassed = true;
                    } else {
                        String name = courseNameMap.getOrDefault(mc.getCourseId(), "课程#" + mc.getCourseId());
                        failedCourseNames.add(name);
                    }
                    totalRequiredScore += historyEn.getFinalScore().doubleValue();
                    requiredCount++;
                } else {
                    allCoursesGraded = false;
                }
            }
        }

        // P0-11: 选修课处理
        int electiveStartedCount = 0; // P2-007: 至少开始学习的选修课数
        for (MicroSpecialtyCourse mc : electiveCourses) {
            Enrollment courseEn = courseEnrollmentRepository.selectOne(
                    new LambdaQueryWrapper<Enrollment>()
                            .eq(Enrollment::getCourseId, mc.getCourseId())
                            .eq(Enrollment::getUserId, en.getUserId())
                            .ne(Enrollment::getEnrollmentStatus, EnrollmentStatus.CANCELLED.getValue()));
            if (courseEn != null) {
                electiveStartedCount++; // P2-007: 有选课记录即视为已开始学习
                if (courseEn.getFinalScore() != null) {
                    totalElectiveScore += courseEn.getFinalScore().doubleValue();
                    electivePassedCount++;
                    creditsEarned = creditsEarned.add(mc.getCredits() != null ? mc.getCredits() : BigDecimal.ZERO);
                }
            }
        }

        // 计算进度（P1-11）
        double progress = progressCount > 0 ? totalProgress / progressCount : 0;
        double avgRequired = requiredCount > 0 ? totalRequiredScore / requiredCount : 0;
        double avgElective = electivePassedCount > 0 ? totalElectiveScore / electivePassedCount : 0;
        double finalScore = avgRequired * 0.7 + avgElective * 0.3;

        // 判定完成（§6.8）
        boolean isCompleted = false;
        String rule = ms.getCompletionRule();
        if (COMPLETION_RULE_ALL_REQUIRED.equals(rule)) {
            isCompleted = coursesCompleted >= (ms.getRequiredCourseCount() != null ? ms.getRequiredCourseCount() : requiredCourses.size());
        } else if (COMPLETION_RULE_CREDITS_MIN.equals(rule)) {
            isCompleted = ms.getMinCredits() != null && creditsEarned.compareTo(ms.getMinCredits()) >= 0;
        } else if (COMPLETION_RULE_MIXED.equals(rule)) {
            boolean requiredOk = coursesCompleted >= (ms.getRequiredCourseCount() != null ? ms.getRequiredCourseCount() : requiredCourses.size());
            boolean creditsOk = ms.getMinCredits() != null && creditsEarned.compareTo(ms.getMinCredits()) >= 0;
            // P2-007: 有选修课时至少已开始学习一门选修课
            boolean electiveStartedOk = electiveCourses.isEmpty() || electiveStartedCount > 0;
            isCompleted = requiredOk && creditsOk && electiveStartedOk;
        }

        int oldVersion = en.getVersion();

        if (isCompleted) {
            // P0-12: 计算 final_grade（§6.8）
            String finalGrade;
            if (finalScore >= 90) finalGrade = FINAL_GRADE_EXCELLENT;
            else if (finalScore >= 75) finalGrade = FINAL_GRADE_GOOD;
            else if (finalScore >= 60) finalGrade = FINAL_GRADE_PASS;
            else finalGrade = FINAL_GRADE_FAIL;

            int affected = enrollmentRepository.update(null,
                    new LambdaUpdateWrapper<MicroSpecialtyEnrollment>()
                            .eq(MicroSpecialtyEnrollment::getId, enrollmentId)
                            .eq(MicroSpecialtyEnrollment::getVersion, oldVersion)
                            .set(MicroSpecialtyEnrollment::getStatus, ENROLLMENT_STATUS_COMPLETED)
                            .set(MicroSpecialtyEnrollment::getProgress, BigDecimal.valueOf(progress))
                            .set(MicroSpecialtyEnrollment::getCreditsEarned, creditsEarned)
                            .set(MicroSpecialtyEnrollment::getCoursesCompleted, coursesCompleted)
                            .set(MicroSpecialtyEnrollment::getFinalScore, BigDecimal.valueOf(finalScore))
                            .set(MicroSpecialtyEnrollment::getFinalGrade, finalGrade)
                            .set(MicroSpecialtyEnrollment::getCompletedAt, LocalDateTime.now())
                            .set(MicroSpecialtyEnrollment::getUpdatedAt, LocalDateTime.now())
                            .setSql("version = version + 1"));
            if (affected == 0) {
                log.warn("并发跳过: enrollment.id={} 已被其他操作修改 (COMPLETED)", enrollmentId);
            } else {
                notificationService.notifyAsync(en.getUserId(), NotificationType.MS_COMPLETED,
                        "微专业已结业", "恭喜！您已完成微专业《" + ms.getTitle() + "》的全部要求", en.getMicroSpecialtyId());
                // 自动颁发证书
                try {
                    enrollmentService.issueCertificate(enrollmentId);
                } catch (Exception e) {
                    log.error("颁发证书失败: enrollmentId={}", enrollmentId, e);
                }
            }
        } else {
            // P0-5: 检查是否可以判定为 FAILED（§9.2.e 步）
            // 所有必修课均已评分 且 没有一门通过 → FAILED
            if (allCoursesGraded && !anyPassed) {
                int failedAffected = enrollmentRepository.update(null,
                        new LambdaUpdateWrapper<MicroSpecialtyEnrollment>()
                                .eq(MicroSpecialtyEnrollment::getId, enrollmentId)
                                .eq(MicroSpecialtyEnrollment::getVersion, oldVersion)
                                .set(MicroSpecialtyEnrollment::getStatus, ENROLLMENT_STATUS_FAILED)
                                .set(MicroSpecialtyEnrollment::getUpdatedAt, LocalDateTime.now())
                                .setSql("version = version + 1"));
                if (failedAffected == 0) {
                    log.warn("并发跳过: enrollment.id={} 已被其他操作修改 (FAILED)", enrollmentId);
                }

                String failedList = failedCourseNames.isEmpty() ? "无" : String.join("、", failedCourseNames);
                notificationService.notifyAsync(en.getUserId(), NotificationType.MS_ENROLLMENT_FAILED,
                        "微专业未通过",
                        "您未通过微专业《" + ms.getTitle() + "》的结业考核。不合格课程：" + failedList,
                        en.getMicroSpecialtyId());
                return;
            }

            // P0 FIX: APPROVED → IN_PROGRESS 自动转换 (学生首门必修课学习时触发)
            if (ENROLLMENT_STATUS_APPROVED.equals(en.getStatus()) && coursesCompleted > 0) {
                int approvedAffected = enrollmentRepository.update(null,
                        new LambdaUpdateWrapper<MicroSpecialtyEnrollment>()
                                .eq(MicroSpecialtyEnrollment::getId, enrollmentId)
                                .eq(MicroSpecialtyEnrollment::getVersion, oldVersion)
                                .set(MicroSpecialtyEnrollment::getStatus, ENROLLMENT_STATUS_IN_PROGRESS)
                                .setSql("version = version + 1"));
                if (approvedAffected == 0) {
                    log.warn("并发跳过: enrollment.id={} 已被其他操作修改 (APPROVED→IN_PROGRESS)", enrollmentId);
                } else {
                    en.setStatus(ENROLLMENT_STATUS_IN_PROGRESS);
                    log.info("enrollment.id={} APPROVED → IN_PROGRESS", enrollmentId);
                }
            }

            // 仅更新进度
            int progressAffected = enrollmentRepository.update(null,
                    new LambdaUpdateWrapper<MicroSpecialtyEnrollment>()
                            .eq(MicroSpecialtyEnrollment::getId, enrollmentId)
                            .eq(MicroSpecialtyEnrollment::getVersion, oldVersion)
                            .set(MicroSpecialtyEnrollment::getProgress, BigDecimal.valueOf(progress))
                            .set(MicroSpecialtyEnrollment::getCreditsEarned, creditsEarned)
                            .set(MicroSpecialtyEnrollment::getCoursesCompleted, coursesCompleted)
                            .set(MicroSpecialtyEnrollment::getFinalScore, BigDecimal.valueOf(finalScore))
                            .set(MicroSpecialtyEnrollment::getUpdatedAt, LocalDateTime.now())
                            .setSql("version = version + 1"));
            if (progressAffected == 0) {
                log.warn("并发跳过: enrollment.id={} 已被其他操作修改 (progress update)", enrollmentId);
            }
        }
    }
}
