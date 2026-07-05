package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.microcourse.dto.BatchOperationResult;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseChapter;
import com.microcourse.entity.CourseReviewLog;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.PluginGrant;
import com.microcourse.enums.CourseStatus;
import com.microcourse.enums.EnrollmentStatus;
import com.microcourse.enums.NotificationType;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.entity.CourseSlide;
import com.microcourse.plugin.interactive.mapper.CourseSlideMapper;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.CourseReviewLogRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.PluginGrantRepository;
import com.microcourse.service.CourseAuditService;
import com.microcourse.service.NotificationService;
import com.microcourse.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CourseAuditServiceImpl implements CourseAuditService {

    private static final Logger LOG = LoggerFactory.getLogger(CourseAuditServiceImpl.class);

    private final CourseRepository courseRepository;
    private final CourseChapterRepository chapterRepository;
    private final CourseReviewLogRepository reviewLogRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final PluginGrantRepository pluginGrantRepository;
    private final CourseSlideMapper courseSlideMapper;
    private final NotificationService notificationService;

    public CourseAuditServiceImpl(CourseRepository courseRepository,
                                  CourseChapterRepository chapterRepository,
                                  CourseReviewLogRepository reviewLogRepository,
                                  EnrollmentRepository enrollmentRepository,
                                  PluginGrantRepository pluginGrantRepository,
                                  CourseSlideMapper courseSlideMapper,
                                  NotificationService notificationService) {
        this.courseRepository = courseRepository;
        this.chapterRepository = chapterRepository;
        this.reviewLogRepository = reviewLogRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.pluginGrantRepository = pluginGrantRepository;
        this.courseSlideMapper = courseSlideMapper;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitForReview(Long id) {
        Course course = getCourseOrThrow(id);
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        CourseStatus current = CourseStatus.fromCode(course.getStatus());
        if (current == null || !current.canTransitionTo(CourseStatus.PENDING_REVIEW)) {
            throw new BusinessException(ErrorCode.COURSE_STATUS_TRANSITION_NOT_ALLOWED, "当前状态不允许提交审核");
        }
        // T11: 提交审核前置完整性校验 — 标题/分类/封面
        if (course.getTitle() == null || course.getTitle().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "请先填写课程标题再提交审核");
        }
        if (course.getCategoryId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "请先选择课程分类再提交审核");
        }
        if (course.getCoverUrl() == null || course.getCoverUrl().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "请先上传课程封面再提交审核");
        }
        long chapterCount = chapterRepository.selectCount(
                new LambdaQueryWrapper<CourseChapter>()
                        .eq(CourseChapter::getCourseId, id));
        if (chapterCount == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "请至少添加一个章节再提交审核");
        }
        Integer currentVersion = course.getVersion();
        int affected = courseRepository.update(null,
                new LambdaUpdateWrapper<Course>()
                        .eq(Course::getId, id)
                        .eq(Course::getStatus, current.getCode())
                        .eq(Course::getVersion, currentVersion)
                        .set(Course::getStatus, CourseStatus.PENDING_REVIEW.getCode())
                        .set(Course::getRejectReason, (String) null)
                        .set(Course::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.COURSE_STATUS_TRANSITION_NOT_ALLOWED);
        }
        LOG.info("课程提交审核, id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        Course course = getCourseOrThrow(id);
        Long currentUserId = SecurityUtil.getCurrentUserId();
        SecurityUtil.assertNotSelf(currentUserId, course.getTeacherId(), "不能审批自己的课程");
        Integer currentVersion = course.getVersion();
        int affected = courseRepository.update(null,
                new LambdaUpdateWrapper<Course>()
                        .eq(Course::getId, id)
                        .eq(Course::getStatus, CourseStatus.PENDING_REVIEW.getCode())
                        .eq(Course::getVersion, currentVersion)
                        .set(Course::getStatus, CourseStatus.APPROVED.getCode())
                        .set(Course::getRejectReason, (String) null)
                        .set(Course::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.COURSE_STATUS_TRANSITION_NOT_ALLOWED, "审核通过失败，请检查课程状态");
        }
        recordReviewLog(id, "APPROVE", CourseStatus.PENDING_REVIEW.getCode(), CourseStatus.APPROVED.getCode(), null);
        if (course.getTeacherId() != null) {
            notificationService.notifyAsync(course.getTeacherId(),
                    NotificationType.COURSE_APPROVED,
                    "课程审核通过", "您的课程《" + course.getTitle() + "》已通过审核", id);
        }
        LOG.info("课程审核通过, id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String reason) {
        Course course = getCourseOrThrow(id);
        Long currentUserId = SecurityUtil.getCurrentUserId();
        SecurityUtil.assertNotSelf(currentUserId, course.getTeacherId(), "不能驳回自己的课程");
        String safeReason = com.microcourse.util.XssSanitizer.sanitizePlainText(reason);
        Integer currentVersion = course.getVersion();
        int affected = courseRepository.update(null,
                new LambdaUpdateWrapper<Course>()
                        .eq(Course::getId, id)
                        .eq(Course::getStatus, CourseStatus.PENDING_REVIEW.getCode())
                        .eq(Course::getVersion, currentVersion)
                        .set(Course::getStatus, CourseStatus.REJECTED.getCode())
                        .set(Course::getRejectReason, safeReason)
                        .set(Course::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.COURSE_STATUS_TRANSITION_NOT_ALLOWED, "驳回失败，请检查课程状态");
        }
        recordReviewLog(id, "REJECT", CourseStatus.PENDING_REVIEW.getCode(), CourseStatus.REJECTED.getCode(), safeReason);
        if (course.getTeacherId() != null) {
            notificationService.notifyAsync(course.getTeacherId(),
                    NotificationType.COURSE_REJECTED,
                    "课程审核驳回", "您的课程《" + course.getTitle() + "》已被驳回，原因：" + safeReason, id);
        }
        LOG.info("课程审核驳回, id={}, reason={}", id, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        Course course = getCourseOrThrow(id);
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        checkPluginGrant(course.getTeacherId(), course.getCourseType());

        if ("INTERACTIVE".equals(course.getCourseType())) {
            LambdaQueryWrapper<CourseSlide> slideQuery = new LambdaQueryWrapper<>();
            slideQuery.eq(CourseSlide::getCourseId, id)
                      .eq(CourseSlide::getStatus, 2);
            long slideCount = courseSlideMapper.selectCount(slideQuery);
            if (slideCount == 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "互动课件尚未就绪，请先上传并等待课件渲染完成");
            }
        }
        Integer currentVersion = course.getVersion();
        int affected = courseRepository.update(null,
                new LambdaUpdateWrapper<Course>()
                        .eq(Course::getId, id)
                        .eq(Course::getVersion, currentVersion)
                        .in(Course::getStatus, CourseStatus.APPROVED.getCode(), CourseStatus.CLOSED.getCode())
                        .set(Course::getStatus, CourseStatus.PUBLISHED.getCode())
                        .set(Course::getPublishedAt, LocalDateTime.now())
                        .set(Course::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.COURSE_STATUS_TRANSITION_NOT_ALLOWED, "发布失败，请检查课程状态");
        }
        recordReviewLog(id, "PUBLISH", course.getStatus(), CourseStatus.PUBLISHED.getCode(), null);
        List<Enrollment> activeEnrollments = enrollmentRepository.selectList(
                new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getCourseId, id)
                        .in(Enrollment::getEnrollmentStatus,
                                EnrollmentStatus.APPROVED.getValue(),
                                EnrollmentStatus.LEGACY_ENROLLED_VALUE));
        for (Enrollment enrollment : activeEnrollments) {
            notificationService.notifyAsync(enrollment.getUserId(),
                    NotificationType.COURSE_PUBLISHED,
                    "课程《" + course.getTitle() + "》已发布",
                    "您已选修该课程，现在可以开始学习", id);
        }
        LOG.info("课程发布成功, id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unpublish(Long id) {
        Course course = getCourseOrThrow(id);
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        CourseStatus current = CourseStatus.fromCode(course.getStatus());
        if (current == null || !current.canTransitionTo(CourseStatus.CLOSED)) {
            throw new BusinessException(ErrorCode.COURSE_STATUS_TRANSITION_NOT_ALLOWED, "当前状态不允许下架");
        }
        Integer currentVersion = course.getVersion();
        int affected = courseRepository.update(null,
                new LambdaUpdateWrapper<Course>()
                        .eq(Course::getId, id)
                        .eq(Course::getStatus, current.getCode())
                        .eq(Course::getVersion, currentVersion)
                        .set(Course::getStatus, CourseStatus.CLOSED.getCode())
                        .set(Course::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.COURSE_STATUS_TRANSITION_NOT_ALLOWED);
        }
        recordReviewLog(id, "UNPUBLISH", current.getCode(), CourseStatus.CLOSED.getCode(), null);
        List<Enrollment> activeEnrollments = enrollmentRepository.selectList(
                new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getCourseId, id)
                        .in(Enrollment::getEnrollmentStatus,
                                EnrollmentStatus.APPROVED.getValue(),
                                EnrollmentStatus.LEGACY_ENROLLED_VALUE));
        for (Enrollment enrollment : activeEnrollments) {
            notificationService.notifyAsync(enrollment.getUserId(),
                    NotificationType.COURSE_UNPUBLISHED,
                    "课程《" + course.getTitle() + "》已下架",
                    "请关注后续公告", id);
        }
        LOG.info("课程下架成功, id={}, operator={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchOperationResult batchApprove(List<Long> ids) {
        BatchOperationResult result = new BatchOperationResult();
        for (Long id : ids) {
            try {
                approve(id);
                result.addSuccess(id);
            } catch (Exception e) {
                LOG.warn("批量审核通过失败, id={}, reason={}", id, e.getMessage());
                result.addFailure(id, e.getMessage());
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchOperationResult batchReject(List<Long> ids, String reason) {
        BatchOperationResult result = new BatchOperationResult();
        for (Long id : ids) {
            try {
                reject(id, reason);
                result.addSuccess(id);
            } catch (Exception e) {
                LOG.warn("批量审核驳回失败, id={}, reason={}", id, e.getMessage());
                result.addFailure(id, e.getMessage());
            }
        }
        return result;
    }

    private void checkPluginGrant(Long teacherId, String courseType) {
        if (courseType == null || "VIDEO".equals(courseType)) return;
        if (SecurityUtil.isAdmin()) return;
        LambdaQueryWrapper<PluginGrant> q = new LambdaQueryWrapper<>();
        q.eq(PluginGrant::getPluginId, "interactive")
                .eq(PluginGrant::getGrantType, "TEACHER")
                .eq(PluginGrant::getGranteeId, teacherId);
        if (pluginGrantRepository.selectCount(q) == 0) {
            throw new BusinessException(ErrorCode.PLUGIN_NO_GRANT);
        }
    }

    private Course getCourseOrThrow(Long id) {
        Course course = courseRepository.selectById(id);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        return course;
    }

    private void recordReviewLog(Long courseId, String action, Integer previousStatus, Integer newStatus, String detail) {
        CourseReviewLog log = new CourseReviewLog();
        log.setCourseId(courseId);
        log.setAction(action);
        log.setPreviousStatus(previousStatus);
        log.setNewStatus(newStatus);
        try {
            log.setReviewerId(SecurityUtil.getCurrentUserId());
        } catch (Exception e) {
            log.setReviewerId(null);
        }
        log.setReason(detail);
        log.setCreatedAt(LocalDateTime.now());
        reviewLogRepository.insert(log);
    }
}
