package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.EnrollmentCreateRequest;
import com.microcourse.dto.EnrollmentQueryRequest;
import com.microcourse.dto.EnrollmentRankingVO;
import com.microcourse.dto.EnrollmentUpdateRequest;
import com.microcourse.dto.EnrollmentVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.StudentDetailVO;
import com.microcourse.entity.Course;
import com.microcourse.entity.Enrollment;
import com.microcourse.enums.EnrollmentStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.metrics.EnrollmentMetrics;
import com.microcourse.repository.ClassesRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.MajorRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.EnrollmentLifecycleService;
import com.microcourse.service.EnrollmentQueryService;
import com.microcourse.service.EnrollmentService;
import com.microcourse.service.EnrollmentStatsService;
import com.microcourse.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 选课业务 Service 实现。
 *
 * <p>职责：仅包含查询/统计/导出 + 核心写操作委托。
 * 所有状态机写操作（选课/更新/退课/候补晋升）委托给 {@link EnrollmentLifecycleService}。</p>
 *
 * <p>拆分原因：原始文件 819 行超过项目 800 行/文件约束。
 * 拆分后此文件 ≤ 750 行，新文件 ≤ 450 行。</p>
 */
@Service
public class EnrollmentServiceImpl implements EnrollmentService {

    private static final Logger LOG = LoggerFactory.getLogger(EnrollmentServiceImpl.class);

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final ClassesRepository classesRepository;
    private final MajorRepository majorRepository;
    private final EnrollmentStatsService statsService;
    private final EnrollmentQueryService queryService;
    private final EnrollmentMetrics metrics;
    private final EnrollmentLifecycleService lifecycleService;

    public EnrollmentServiceImpl(EnrollmentRepository enrollmentRepository,
                                  CourseRepository courseRepository,
                                  UserRepository userRepository,
                                  ClassesRepository classesRepository,
                                  MajorRepository majorRepository,
                                  EnrollmentStatsService statsService,
                                  EnrollmentQueryService queryService,
                                  EnrollmentMetrics metrics,
                                  EnrollmentLifecycleService lifecycleService) {
        this.enrollmentRepository = enrollmentRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.classesRepository = classesRepository;
        this.majorRepository = majorRepository;
        this.statsService = statsService;
        this.queryService = queryService;
        this.metrics = metrics;
        this.lifecycleService = lifecycleService;
    }

    // ============ 写操作（委托 EnrollmentLifecycleService） ============

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EnrollmentVO enroll(EnrollmentCreateRequest request) {
        // ★ 业务逻辑审计 P0-2 增强：可观测性 — Timer 记录完整耗时（含行级锁）
        io.micrometer.core.instrument.Timer.Sample sample = io.micrometer.core.instrument.Timer.start();
        boolean success = false;
        try {
            EnrollmentVO result = lifecycleService.doEnroll(request);
            success = true;
            return result;
        } finally {
            sample.stop(metrics.enrollTimer());
            if (success) {
                metrics.recordSuccess();
            } else {
                metrics.recordError();
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EnrollmentVO updateEnrollment(Long id, EnrollmentUpdateRequest request) {
        return lifecycleService.updateEnrollment(id, request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelEnrollment(Long id, Long currentUserId) {
        lifecycleService.cancelEnrollment(id, currentUserId);
    }

    @Override
    public void promoteFirstWaitlistToEnrolled(Long courseId) {
        lifecycleService.promoteFirstWaitlistToEnrolled(courseId);
    }

    // ============ 只读查询（委托 EnrollmentQueryService） ============

    @Override
    public List<EnrollmentVO> getMyEnrollments(Long userId, Boolean completed) {
        return queryService.getMyEnrollments(userId, completed);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<EnrollmentVO> getEnrollmentPage(EnrollmentQueryRequest query) {
        // SECURITY: TEACHER 只能查自己课程的学员，强制覆写 teacherId
        if (SecurityUtil.hasRole("TEACHER")) {
            query.setTeacherId(SecurityUtil.getCurrentUserId());
        }
        return queryService.getEnrollmentPage(query);
    }

    @Override
    public List<EnrollmentVO> getCourseEnrollments(Long courseId) {
        return queryService.getCourseEnrollments(courseId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnrollmentVO> getCourseEnrollmentsWithOwnerCheck(Long courseId) {
        // SECURITY: TEACHER 非 ADMIN 必须为课程 owner
        if (SecurityUtil.hasRole("TEACHER") && !SecurityUtil.isAdmin()) {
            assertCourseOwnership(courseId);
        }
        return queryService.getCourseEnrollments(courseId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<EnrollmentVO> getCourseEnrollmentPage(Long courseId, int page, int size) {
        // SECURITY: TEACHER 必须为课程 owner；ADMIN/ACADEMIC 跳过
        if (SecurityUtil.hasRole("TEACHER")) {
            assertCourseOwnership(courseId);
        }
        return queryService.getCourseEnrollmentPage(courseId, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnrollmentRankingVO> getCourseRanking(Long courseId, int limit, Long currentUserId) {
        return queryService.getCourseRanking(courseId, limit, currentUserId);
    }

    // ============ 统计方法（委托 EnrollmentStatsService） ============

    @Override
    @Transactional(readOnly = true)
    public long countByTeacherId(Long teacherId) {
        return statsService.countByTeacherId(teacherId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countCompletedByTeacherId(Long teacherId) {
        return statsService.countCompletedByTeacherId(teacherId);
    }

    @Override
    @Transactional(readOnly = true)
    public double getAvgScoreByTeacherId(Long teacherId) {
        return statsService.getAvgScoreByTeacherId(teacherId);
    }

    // ============ 其他方法 ============

    @Override
    @Transactional(readOnly = true)
    public StudentDetailVO getStudentDetail(Long userId) {
        // TEACHER 仅能查询自己课程中的学生
        if (SecurityUtil.hasRole("TEACHER") && !SecurityUtil.isAdmin()) {
            assertStudentInTeachersCourses(SecurityUtil.getCurrentUserId(), userId);
        }
        return queryService.getStudentDetail(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnrollmentVO> getStudentProgress(Long userId) {
        List<EnrollmentVO> enrollments = queryService.getMyEnrollments(userId, null);

        // P0 SECURITY FIX: TEACHER 仅能查询自己课程中该学生的进度
        // - 如果学生完全不在教师课程中 → 403 严格隔离
        // - 如果有部分共同课程 → 200 + 仅返回共同课程的进度(宽松过滤)
        if (SecurityUtil.hasRole("TEACHER") && !SecurityUtil.isAdmin()) {
            Long currentUserId = SecurityUtil.getCurrentUserId();
            List<Long> teacherCourseIds = courseRepository.selectList(
                    new LambdaQueryWrapper<Course>()
                            .eq(Course::getTeacherId, currentUserId)
                            .isNull(Course::getDeletedAt)
                            .select(Course::getId)
            ).stream().map(Course::getId).collect(Collectors.toList());

            // 学生完全不在教师课程中 → 403
            if (enrollments.stream().noneMatch(e -> teacherCourseIds.contains(e.getCourseId()))) {
                throw new BusinessException(ErrorCode.NO_PERMISSION, "该学生不在您的授课课程中");
            }

            // 部分共同 → 过滤
            return enrollments.stream()
                    .filter(e -> teacherCourseIds.contains(e.getCourseId()))
                    .collect(Collectors.toList());
        }

        // ADMIN / ACADEMIC: 全量返回
        return enrollments;
    }

    @Override
    @Transactional(readOnly = true)
    public EnrollmentVO getEnrollmentDetail(Long id) {
        Enrollment enrollment = enrollmentRepository.selectById(id);
        if (enrollment == null) {
            throw new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND);
        }
        EnrollmentVO vo = convertToVO(enrollment);
        // 角色级权限校验：ADMIN/ACADEMIC 无限制，TEACHER 必须为课程 owner，STUDENT 仅本人
        if (SecurityUtil.isAdmin() || SecurityUtil.hasRole("ACADEMIC")) {
            return vo;
        }
        if (SecurityUtil.hasRole("TEACHER")) {
            assertCourseOwnership(vo.getCourseId());
            return vo;
        }
        // STUDENT：仅本人
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (vo.getUserId() == null || !vo.getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        return vo;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findActiveUserIdsByCourseId(Long courseId) {
        return enrollmentRepository.findActiveUserIdsByCourseId(
                courseId,
                EnrollmentStatus.APPROVED.getValue(),
                EnrollmentStatus.APPROVED.getValue(),
                EnrollmentStatus.COMPLETED.getValue());
    }

    @Override
    public void assertCourseOwnership(Long courseId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        // ADMIN / ACADEMIC: 跳过课程归属校验
        if (SecurityUtil.isAdmin() || SecurityUtil.hasRole("ACADEMIC")) {
            return;
        }
        Long currentUserId = SecurityUtil.getCurrentUserId();
        Long teacherId = course.getTeacherId();
        // TEACHER 必须为课程 owner；teacherId 为 null 时无权访问
        if (teacherId == null || !java.util.Objects.equals(teacherId, currentUserId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void assertStudentInTeachersCourses(Long teacherId, Long studentId) {
        long count = enrollmentRepository.countByTeacherAndStudent(teacherId, studentId,
                EnrollmentStatus.LEGACY_ENROLLED_VALUE,   // "ENROLLED"（V148 历史兼容）— 修正: 原误传 APPROVED 导致存量数据漏查
                EnrollmentStatus.APPROVED.getValue(),
                EnrollmentStatus.COMPLETED.getValue());
        if (count == 0) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "该学生不在您的授课课程中");
        }
    }

    @Override
    public void exportEnrollments(Long courseId, HttpServletResponse response) throws IOException {
        queryService.exportEnrollments(courseId, response);
    }

    // ============ 私有工具 ============

    private EnrollmentVO convertToVO(Enrollment enrollment) {
        return com.microcourse.util.EnrollmentConverter.convertToVO(
                enrollment, courseRepository, userRepository, classesRepository, majorRepository);
    }
}
