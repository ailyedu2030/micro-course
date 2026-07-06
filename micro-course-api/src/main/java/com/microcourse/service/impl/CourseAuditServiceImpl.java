package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.microcourse.dto.BatchOperationResult;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseChapter;
import com.microcourse.entity.CourseReviewLog;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.Exercise;
import com.microcourse.entity.PluginGrant;
import com.microcourse.entity.User;
import com.microcourse.entity.Video;
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
import com.microcourse.repository.ExerciseRepository;
import com.microcourse.repository.PluginGrantRepository;
import com.microcourse.repository.VideoRepository;
import com.microcourse.service.CourseAuditService;
import com.microcourse.service.CourseStateMachine;
import com.microcourse.service.CourseStateMachine.TransitionContext;
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
    private final VideoRepository videoRepository;
    private final ExerciseRepository exerciseRepository;
    private final CourseSlideMapper courseSlideMapper;
    private final CourseStateMachine courseStateMachine;
    private final NotificationService notificationService;

    public CourseAuditServiceImpl(CourseRepository courseRepository,
                                  CourseChapterRepository chapterRepository,
                                  CourseReviewLogRepository reviewLogRepository,
                                  EnrollmentRepository enrollmentRepository,
                                  PluginGrantRepository pluginGrantRepository,
                                  VideoRepository videoRepository,
                                  ExerciseRepository exerciseRepository,
                                  CourseSlideMapper courseSlideMapper,
                                  CourseStateMachine courseStateMachine,
                                  NotificationService notificationService) {
        this.courseRepository = courseRepository;
        this.chapterRepository = chapterRepository;
        this.reviewLogRepository = reviewLogRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.pluginGrantRepository = pluginGrantRepository;
        this.videoRepository = videoRepository;
        this.exerciseRepository = exerciseRepository;
        this.courseSlideMapper = courseSlideMapper;
        this.courseStateMachine = courseStateMachine;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitForReview(Long id) {
        Course course = getCourseOrThrow(id);
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        // 【状态机重构】所有守卫下沉到 CourseStateMachineConfig 注册, 此处只委托
        User actor = SecurityUtil.getCurrentUser();
        courseStateMachine.transition(id, CourseStatus.PENDING_REVIEW, actor, TransitionContext.empty());
        LOG.info("课程提交审核, id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        Course course = getCourseOrThrow(id);
        // 【状态机重构】自审批阻断下沉到 CourseStateMachine.transition 内部
        User actor = SecurityUtil.getCurrentUser();
        courseStateMachine.transition(id, CourseStatus.APPROVED, actor, TransitionContext.empty());
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
        String safeReason = com.microcourse.util.XssSanitizer.sanitizePlainText(reason);
        // 【状态机重构】长度校验和自审批下沉到 CourseStateMachine 守卫
        User actor = SecurityUtil.getCurrentUser();
        courseStateMachine.transition(id, CourseStatus.REJECTED, actor, TransitionContext.ofReject(safeReason));
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

        /* ---- 定价审批状态检查 (业务守卫, 与状态机守卫并行) ---- */
        if (!Boolean.TRUE.equals(course.getIsFree()) && course.getPricingStatus() != null
                && !"APPROVED".equals(course.getPricingStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "课程定价尚未审批通过，无法发布");
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
        // 【状态机重构】所有状态变更守卫 (含 CLOSED→PUBLISHED 历史校验, 自审批阻断, 乐观锁) 下沉到 CourseStateMachine
        Integer previousStatus = course.getStatus();
        User actor = SecurityUtil.getCurrentUser();
        courseStateMachine.transition(id, CourseStatus.PUBLISHED, actor, TransitionContext.empty());
        recordReviewLog(id, "PUBLISH", previousStatus, CourseStatus.PUBLISHED.getCode(), null);
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
        /* ---- 【C-1 修复】OFFLINE 不要求互动课件插件授权 ---- */
        /* 【根因】条件 `"VIDEO".equals(courseType)` 只排除 VIDEO，导致 OFFLINE 也被要求 interactive 插件授权 */
        /* 【修复】改为只对 INTERACTIVE 类型检查，其他类型自动跳过 */
        /* 【防止再发】条件翻转 `!"INTERACTIVE".equals` 确保未来新增类型也不会误触发 */
        if (courseType == null || !"INTERACTIVE".equals(courseType)) return;
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
