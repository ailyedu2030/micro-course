package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.CourseCreateRequest;
import com.microcourse.dto.CoursePageQuery;
import com.microcourse.dto.CoursePricingInfoVO;
import com.microcourse.dto.CoursePricingRequest;
import com.microcourse.dto.CourseStatsVO;
import com.microcourse.dto.CourseUpdateRequest;
import com.microcourse.dto.CourseVO;
import com.microcourse.dto.PricingForAdopterVO;
import com.microcourse.dto.PageResult;
import com.microcourse.entity.Course;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.enums.CourseStatus;
import com.microcourse.enums.EnrollmentStatus;
import com.microcourse.enums.NotificationType;
import java.io.IOException;
import java.time.LocalDateTime;
import jakarta.servlet.http.HttpServletResponse;
import com.microcourse.service.CourseAdminService;
import com.microcourse.service.CoursePricingService;
import com.microcourse.service.CourseQueryService;
import com.microcourse.service.CourseService;
import com.microcourse.service.EnrollmentService;
import com.microcourse.service.NotificationService;
import com.microcourse.util.CourseCacheConstants;
import com.microcourse.util.RedisUtil;
import com.microcourse.util.SecurityUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class CourseServiceImpl implements CourseService {

    private static final Logger LOG = LoggerFactory.getLogger(CourseServiceImpl.class);

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final RedisUtil redisUtil;
    private final CoursePricingService pricingService;
    private final CourseQueryService queryService;
    private final CourseAdminService adminService;
    private final NotificationService notificationService;
    @Lazy
    private final EnrollmentService enrollmentService;

    public CourseServiceImpl(CourseRepository courseRepository,
                             UserRepository userRepository,
                             EnrollmentRepository enrollmentRepository,
                             RedisUtil redisUtil,
                             CoursePricingService pricingService,
                             CourseQueryService queryService,
                             CourseAdminService adminService,
                              NotificationService notificationService,
                              @Lazy EnrollmentService enrollmentService) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.redisUtil = redisUtil;
        this.pricingService = pricingService;
        this.queryService = queryService;
        this.adminService = adminService;
        this.notificationService = notificationService;
        this.enrollmentService = enrollmentService;
    }

    /**
     * 清除课程详情缓存。Redis 故障时仅记录告警，不阻塞主流程。
     */
    public void evictCourseCache(Long courseId) {
        if (courseId == null) return;
        try {
            redisUtil.delete(CourseCacheConstants.COURSE_CACHE_PREFIX + courseId);
            redisUtil.delete(CourseCacheConstants.COURSE_STATS_CACHE_PREFIX + courseId);
        } catch (Exception e) {
            LOG.warn("[Round9-2] 课程缓存清除失败, courseId={}", courseId, e);
        }
    }

    /**
     * 在事务提交后清除缓存，规避 cache-aside 竞态。
     */
    private void evictCourseCacheAfterCommit(Long courseId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            evictCourseCache(courseId);
                        }
                    });
        } else {
            evictCourseCache(courseId);
        }
    }

    /* ================================================================
     *  Delegates to CourseAdminService
     * ================================================================ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseVO create(CourseCreateRequest request) {
        return adminService.create(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseVO update(Long id, CourseUpdateRequest request) {
        CourseVO vo = adminService.update(id, request);
        evictCourseCacheAfterCommit(id);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        adminService.delete(id);
        evictCourseCacheAfterCommit(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseVO copy(Long id) {
        CourseVO vo = adminService.copy(id);
        evictCourseCacheAfterCommit(id);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseVO updateCover(Long id, org.springframework.web.multipart.MultipartFile file) {
        CourseVO vo = adminService.updateCover(id, file);
        evictCourseCacheAfterCommit(id);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        CourseStatus target = CourseStatus.fromCode(status);
        // 【S4 修复】status=1 (PENDING_REVIEW) 和 status=4 (PUBLISHED) 是业务转换，
        // 必须走专用端点 /submit 和 /publish，绕过会有安全风险
        if (target == CourseStatus.PENDING_REVIEW || target == CourseStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.COURSE_STATUS_TRANSITION_NOT_ALLOWED,
                    "请使用 /submit 或 /publish 专用端点进行此状态变更");
        }
        // 非 admin 仅可关闭/归档，不能发布
        if (target == CourseStatus.PUBLISHED && !SecurityUtil.isAdmin()) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        adminService.updateStatus(id, status);
        evictCourseCacheAfterCommit(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitForReview(Long id) {
        adminService.submitForReview(id);
        evictCourseCacheAfterCommit(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        adminService.approve(id);
        evictCourseCacheAfterCommit(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String reason) {
        adminService.reject(id, reason);
        evictCourseCacheAfterCommit(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        adminService.publish(id);
        evictCourseCacheAfterCommit(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unpublish(Long id) {
        // 取课程信息（P1-C: 判空避免空指针）
        CourseVO before = getById(id);
        if (before == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        // CLOSED 是合法状态转换（PUBLISHED → CLOSED）
        adminService.updateStatus(id, CourseStatus.CLOSED.getCode());
        evictCourseCacheAfterCommit(id);

        // 异步通知在学学生（失败不影响主流程，只记录日志）
        try {
            List<Long> userIds = enrollmentService.findActiveUserIdsByCourseId(id);
            if (userIds != null && !userIds.isEmpty()) {
                String title = "课程下架通知";
                String content = String.format("您正在学习的《%s》已下架,如有疑问请联系管理员。", before.getTitle());
                for (Long userId : userIds) {
                    try {
                        notificationService.notifyAsync(userId,
                                NotificationType.COURSE_UNPUBLISHED,
                                title, content, id);
                    } catch (Exception e) {
                        LOG.warn("单条下架通知失败: userId={}, err={}", userId, e.getMessage());
                    }
                }
                LOG.info("课程下架通知已派发 (异步): courseId={}, 收件人数={}", id, userIds.size());
            }
        } catch (Exception e) {
            LOG.warn("课程下架通知失败: courseId={}, err={}", id, e.getMessage());
        }
    }

    /**
     * P1I-040 修复：检查审核超时（超过 48 小时未处理的待审核课程）。
     * 可通过定时任务（如 @Scheduled）定期调用。
     */
    @Override
    @Transactional(readOnly = true)
    public void checkReviewTimeout() {
        LOG.info("[P1I-040] 开始检查课程审核超时...");
        List<Course> pendingCourses = courseRepository.selectList(
                new LambdaQueryWrapper<Course>()
                        .eq(Course::getStatus, CourseStatus.PENDING_REVIEW.getCode())
                        .lt(Course::getUpdatedAt, LocalDateTime.now().minusHours(48)));
        for (Course course : pendingCourses) {
            try {
                User teacher = userRepository.selectById(course.getTeacherId());
                if (teacher != null) {
                    String title = "课程审核超时提醒";
                    String content = String.format("您的课程《%s》提交审核已超过 48 小时，请提醒教务处处理。", course.getTitle());
                    notificationService.notifyAsync(teacher.getId(), NotificationType.COURSE_REVIEW_REMINDER,
                            title, content, course.getId());
                    LOG.warn("[P1I-040] 课程审核超时提醒: courseId={}, title={}",
                            course.getId(), course.getTitle());
                }
            } catch (Exception e) {
                LOG.error("[P1I-040] 审核超时提醒发送失败: courseId={}", course.getId(), e);
            }
        }
        LOG.info("[P1I-040] 课程审核超时检查完成，共 {} 条超时记录", pendingCourses.size());
    }

    /* ================================================================
     *  Query methods (remain local)
     * ================================================================ */

    @Override
    public PageResult<CourseVO> page(CoursePageQuery query) {
        return queryService.page(query);
    }

    @Override
    public CourseVO getById(Long id) {
        return queryService.getById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public CourseStatsVO computeStats(Long courseId) {
        // TEACHER owner 校验（移自 Controller getCourseStats）
        if (SecurityUtil.hasRole("TEACHER") && !SecurityUtil.isAdmin()) {
            enrollmentService.assertCourseOwnership(courseId);
        }

        String cacheKey = CourseCacheConstants.COURSE_STATS_CACHE_PREFIX + courseId;
        try {
            Object cached = redisUtil.get(cacheKey);
            if (cached instanceof CourseStatsVO) {
                return (CourseStatsVO) cached;
            }
        } catch (Exception e) {
            LOG.warn("[Round9-2] 课程统计缓存读取失败，降级查询 DB, courseId={}", courseId, e);
        }

        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        CourseStatsVO vo = new CourseStatsVO();
        vo.setCourseId(courseId);
        vo.setCourseTitle(course.getTitle());
        if (course.getTeacherId() != null) {
            User teacher = userRepository.selectById(course.getTeacherId());
            if (teacher != null) {
                vo.setTeacherName(teacher.getRealName());
            }
        }

        long total = enrollmentRepository.selectCount(
                new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getCourseId, courseId)
                        .ne(Enrollment::getEnrollmentStatus, EnrollmentStatus.CANCELLED.getValue()));
        long completed = enrollmentRepository.selectCount(
                new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getCourseId, courseId)
                        .eq(Enrollment::getCompleted, true));
        vo.setEnrollmentCount(total);
        vo.setCompletionRate(total > 0 ? (double) completed / total : 0.0);

        Double avg = enrollmentRepository.avgScoreByCourseId(courseId);
        vo.setAvgScore(avg != null ? avg : 0.0);

        try {
            redisUtil.set(cacheKey, vo, CourseCacheConstants.COURSE_STATS_CACHE_TTL, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOG.warn("[Round9-2] 课程统计缓存写入失败, courseId={}", courseId, e);
        }
        return vo;
    }

    /* ================================================================
     *  Pricing delegates
     * ================================================================ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePricing(Long courseId, CoursePricingRequest request) {
        pricingService.updatePricing(courseId, request);
        evictCourseCacheAfterCommit(courseId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitPricingForReview(Long courseId) {
        pricingService.submitPricingForReview(courseId);
        evictCourseCacheAfterCommit(courseId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewPricing(Long courseId, boolean approved, String reason) {
        pricingService.reviewPricing(courseId, approved, reason);
        evictCourseCacheAfterCommit(courseId);
    }

    @Override
    public PricingForAdopterVO getPricingForAdopter(Long courseId) {
        return pricingService.getPricingForAdopter(courseId);
    }

    @Override
    public CoursePricingInfoVO getMyPricing(Long courseId) {
        return pricingService.getMyPricing(courseId);
    }

    /* ================================================================
     *  Export
     * ================================================================ */

    @Override
    public void exportCourses(HttpServletResponse response) throws IOException {
        List<Course> courses = courseRepository.selectList(
                new LambdaQueryWrapper<Course>().orderByDesc(Course::getCreatedAt).last("LIMIT 10000"));

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=courses_export.xlsx");

        cn.hutool.poi.excel.ExcelWriter writer = cn.hutool.poi.excel.ExcelUtil.getWriter(true);
        try {
            writer.addHeaderAlias("id", "课程ID");
            writer.addHeaderAlias("title", "课程标题");
            writer.addHeaderAlias("description", "课程描述");
            writer.addHeaderAlias("difficulty", "难度");
            writer.addHeaderAlias("price", "价格");
            writer.addHeaderAlias("status", "状态");
            writer.addHeaderAlias("avgRating", "平均评分");
            writer.addHeaderAlias("studentCount", "选课人数");
            writer.addHeaderAlias("createdAt", "创建时间");
            writer.addHeaderAlias("updatedAt", "更新时间");
            writer.addHeaderAlias("publishedAt", "发布时间");

            java.util.ArrayList<CourseVO> rows = new java.util.ArrayList<>();
            for (Course course : courses) {
                CourseVO vo = new CourseVO();
                vo.setId(course.getId());
                vo.setTitle(course.getTitle());
                vo.setDescription(course.getDescription());
                vo.setDifficulty(course.getDifficulty());
                vo.setPrice(course.getPrice());
                vo.setStatus(course.getStatus());
                vo.setAvgRating(course.getAvgRating());
                vo.setStudentCount(course.getStudentCount());
                vo.setCreatedAt(course.getCreatedAt());
                vo.setUpdatedAt(course.getUpdatedAt());
                vo.setPublishedAt(course.getPublishedAt());
                rows.add(vo);
            }

            writer.write(rows, true);
            writer.flush(response.getOutputStream());
        } finally {
            writer.close();
        }
    }
}
