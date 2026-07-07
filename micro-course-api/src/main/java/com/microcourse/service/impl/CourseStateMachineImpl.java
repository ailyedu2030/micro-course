package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseChapter;
import com.microcourse.entity.Exercise;
import com.microcourse.entity.User;
import com.microcourse.entity.Video;
import com.microcourse.plugin.interactive.entity.CourseSlide;
import com.microcourse.plugin.interactive.mapper.CourseSlideMapper;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.ExerciseRepository;
import com.microcourse.repository.VideoRepository;
import com.microcourse.enums.CourseStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseRepository;
import com.microcourse.service.CourseStateMachine;
import com.microcourse.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * 课程状态机实现
 *
 * <p>执行流程:
 * <ol>
 *   <li>加载课程</li>
 *   <li>canTransitionTo 白名单检查</li>
 *   <li>自审批阻断 (actor == teacher 且是审核类操作)</li>
 *   <li>注册的业务守卫 hook</li>
 *   <li>乐观锁 CAS UPDATE</li>
 *   <li>触发副作用 (lastPublishedAt 更新)</li>
 * </ol>
 */
@Service
@Primary
public class CourseStateMachineImpl implements CourseStateMachine {

    private static final Logger LOG = LoggerFactory.getLogger(CourseStateMachineImpl.class);

    private final CourseRepository courseRepository;
    private final VideoRepository videoRepository;
    private final ExerciseRepository exerciseRepository;
    private final CourseChapterRepository chapterRepository;
    private final CourseSlideMapper courseSlideMapper;
    /** (from, to) → List<guard> */
    private final Map<CourseStatus, Map<CourseStatus, List<BiFunction<Course, TransitionContext, List<String>>>>> guards
            = new EnumMap<>(CourseStatus.class);

    public CourseStateMachineImpl(CourseRepository courseRepository,
                                  VideoRepository videoRepository,
                                  ExerciseRepository exerciseRepository,
                                  CourseChapterRepository chapterRepository,
                                  CourseSlideMapper courseSlideMapper) {
        this.courseRepository = courseRepository;
        this.videoRepository = videoRepository;
        this.exerciseRepository = exerciseRepository;
        this.chapterRepository = chapterRepository;
        this.courseSlideMapper = courseSlideMapper;
        // 守卫注册: 内嵌到构造函数, 保证无论通过 @Service 还是 @Bean 都能注册守卫
        registerInternalGuards();
    }

    /**
     * 内嵌守卫注册 (避免依赖外部 Config bean, 确保所有实例都有守卫)
     */
    private void registerInternalGuards() {
        // DRAFT → PENDING_REVIEW: 完整性 + 章节内容校验
        registerGuard(CourseStatus.DRAFT, CourseStatus.PENDING_REVIEW, (course, ctx) -> {
            List<String> errors = new ArrayList<>();
            if (course.getTitle() == null || course.getTitle().isBlank()) errors.add("课程标题不能为空");
            if (course.getCategoryId() == null) errors.add("课程分类未选择");
            if (course.getCoverUrl() == null || course.getCoverUrl().isBlank()) errors.add("课程封面未上传");
            if (chapterRepository != null) {
                long chapterCount = chapterRepository.selectCount(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CourseChapter>()
                                .eq(CourseChapter::getCourseId, course.getId()));
                if (chapterCount == 0) errors.add("至少添加一个章节");
            }
            // 【审查修复】OFFLINE 课程没有视频/练习/课件, 跳过内容检查
            if (!"OFFLINE".equals(course.getCourseType())) {
                long videoCount = videoRepository != null ? videoRepository.selectCount(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.microcourse.entity.Video>()
                                .eq(com.microcourse.entity.Video::getCourseId, course.getId())
                                .isNotNull(com.microcourse.entity.Video::getChapterId)) : 0;
                long exerciseCount = exerciseRepository != null ? exerciseRepository.selectCount(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.microcourse.entity.Exercise>()
                                .eq(com.microcourse.entity.Exercise::getCourseId, course.getId())) : 0;
                long slideCount = courseSlideMapper != null ? courseSlideMapper.selectCount(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.microcourse.plugin.interactive.entity.CourseSlide>()
                                .eq(com.microcourse.plugin.interactive.entity.CourseSlide::getCourseId, course.getId())) : 0;
                if (videoCount + exerciseCount + slideCount == 0) {
                    errors.add("至少一个章节下必须有视频/练习/课件");
                }
            }
            return errors;
        });

        // PENDING_REVIEW → REJECTED: 驳回原因 ≥ 10 字符
        registerGuard(CourseStatus.PENDING_REVIEW, CourseStatus.REJECTED, (course, ctx) -> {
            List<String> errors = new ArrayList<>();
            String reason = ctx.getRejectReason();
            if (reason == null || reason.trim().isEmpty()) errors.add("驳回原因不能为空");
            else if (reason.trim().length() < 10) errors.add("驳回原因不能少于 10 字符");
            return errors;
        });

        // CLOSED → PUBLISHED: 必须此前曾经发布过
        registerGuard(CourseStatus.CLOSED, CourseStatus.PUBLISHED, (course, ctx) -> {
            List<String> errors = new ArrayList<>();
            if (course.getLastPublishedAt() == null) errors.add("只有曾经发布过的课程才能重新上架");
            return errors;
        });

        LOG.info("[CourseStateMachine] 守卫注册完成 (3 状态对, 3 守卫函数)");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Course transition(Long courseId, CourseStatus targetStatus, User actor, TransitionContext context) {
        if (context == null) context = TransitionContext.empty();
        if (targetStatus == null) {
            throw new BusinessException(ErrorCode.COURSE_INVALID_STATUS);
        }

        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        CourseStatus current = CourseStatus.fromCode(course.getStatus());
        if (current == null) {
            throw new BusinessException(ErrorCode.COURSE_INVALID_STATUS);
        }

        // Step 1: canTransitionTo 白名单
        if (!current.canTransitionTo(targetStatus)) {
            throw new BusinessException(ErrorCode.COURSE_STATUS_TRANSITION_NOT_ALLOWED,
                    "不允许从 " + current + " 转换到 " + targetStatus);
        }

        // Step 2: 自审批阻断
        if (actor != null && actor.getId() != null && actor.getId().equals(course.getTeacherId())
                && isSelfApprovalAction(current, targetStatus)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "不能对自己的课程执行审批操作");
        }

        // Step 3: 注册的业务守卫
        TransitionGuardResult guardResult = runGuards(current, targetStatus, course, context);
        if (guardResult != TransitionGuardResult.ALLOWED) {
            // 守卫阻断: 业务条件不满足, 使用 BAD_REQUEST_PARAM 而非 INVALID_STATUS_TRANSITION
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                    "业务守卫未通过: " + guardResult);
        }

        // Step 4: 乐观锁 CAS 更新
        Integer currentVersion = course.getVersion();
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<Course> updateWrapper = new LambdaUpdateWrapper<Course>()
                .eq(Course::getId, courseId)
                .eq(Course::getStatus, current.getCode())
                .eq(Course::getVersion, currentVersion)
                .set(Course::getStatus, targetStatus.getCode())
                .set(Course::getUpdatedAt, now)
                .setSql("version = version + 1");

        // 副作用: 发布时更新 publishedAt + lastPublishedAt
        if (targetStatus == CourseStatus.PUBLISHED) {
            updateWrapper.set(Course::getPublishedAt, now)
                    .set(Course::getLastPublishedAt, now);
        }
        // 副作用: 进入 REJECTED 时写入 rejectReason
        if (targetStatus == CourseStatus.REJECTED) {
            updateWrapper.set(Course::getRejectReason, context.getRejectReason());
        } else {
            // 离开 REJECTED 状态时清空 rejectReason
            updateWrapper.set(Course::getRejectReason, (String) null);
        }

        int affected = courseRepository.update(null, updateWrapper);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.COURSE_STATUS_TRANSITION_NOT_ALLOWED,
                    "状态变更失败, 可能存在并发修改");
        }

        LOG.info("[CourseStateMachine] courseId={} {}→{} actor={}",
                courseId, current, targetStatus, actor != null ? actor.getId() : "system");
        course.setStatus(targetStatus.getCode());
        course.setVersion(currentVersion + 1);
        course.setUpdatedAt(now);
        if (targetStatus == CourseStatus.PUBLISHED) {
            course.setPublishedAt(now);
            course.setLastPublishedAt(now);
        }
        return course;
    }

    @Override
    public TransitionGuardResult checkTransition(Long courseId, CourseStatus targetStatus,
                                                  User actor, TransitionContext context) {
        if (context == null) context = TransitionContext.empty();
        Course course = courseRepository.selectById(courseId);
        if (course == null) return TransitionGuardResult.COURSE_NOT_FOUND;
        CourseStatus current = CourseStatus.fromCode(course.getStatus());
        if (current == null) return TransitionGuardResult.INVALID_TRANSITION;

        if (!current.canTransitionTo(targetStatus)) return TransitionGuardResult.INVALID_TRANSITION;

        if (actor != null && actor.getId() != null && actor.getId().equals(course.getTeacherId())
                && isSelfApprovalAction(current, targetStatus)) {
            return TransitionGuardResult.SELF_APPROVAL_BLOCKED;
        }

        TransitionGuardResult guardResult = runGuards(current, targetStatus, course, context);
        return guardResult;
    }

    @Override
    public void registerGuard(CourseStatus from, CourseStatus to,
                              BiFunction<Course, TransitionContext, List<String>> guard) {
        guards.computeIfAbsent(from, k -> new EnumMap<>(CourseStatus.class))
                .computeIfAbsent(to, k -> new ArrayList<>())
                .add(guard);
    }

    private TransitionGuardResult runGuards(CourseStatus from, CourseStatus to,
                                            Course course, TransitionContext context) {
        Map<CourseStatus, List<BiFunction<Course, TransitionContext, List<String>>>> toGuards = guards.get(from);
        if (toGuards == null) return TransitionGuardResult.ALLOWED;
        List<BiFunction<Course, TransitionContext, List<String>>> guardList = toGuards.get(to);
        if (guardList == null || guardList.isEmpty()) return TransitionGuardResult.ALLOWED;
        for (BiFunction<Course, TransitionContext, List<String>> guard : guardList) {
            List<String> errors = guard.apply(course, context);
            if (errors != null && !errors.isEmpty()) {
                LOG.warn("[CourseStateMachine] guard blocked courseId={} {}→{} errors={}",
                        course.getId(), from, to, errors);
                return TransitionGuardResult.BLOCKED_BY_GUARD;
            }
        }
        return TransitionGuardResult.ALLOWED;
    }

    /**
     * 自审批阻断: 教师/管理员不能审批/驳回/发布自己创建的课程
     */
    private boolean isSelfApprovalAction(CourseStatus from, CourseStatus to) {
        // 审批/驳回/发布/重审 这类"裁判员"操作
        return (from == CourseStatus.PENDING_REVIEW && to == CourseStatus.APPROVED)
                || (from == CourseStatus.PENDING_REVIEW && to == CourseStatus.REJECTED)
                || (to == CourseStatus.PUBLISHED);
    }
}