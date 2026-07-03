package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.microcourse.dto.CourseCreateRequest;
import com.microcourse.dto.CourseUpdateRequest;
import com.microcourse.dto.CourseVO;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseCategory;
import com.microcourse.entity.CourseChapter;
import com.microcourse.entity.User;
import com.microcourse.enums.CourseStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseCategoryRepository;
import com.microcourse.entity.CourseReviewLog;
import com.microcourse.entity.Enrollment;
import com.microcourse.enums.EnrollmentStatus;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.CourseReviewLogRepository;
import com.microcourse.repository.CourseReviewRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.PluginGrantRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.CourseAdminService;
import com.microcourse.service.NotificationService;
import com.microcourse.util.FileUploadUtil;
import com.microcourse.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 课程管理（CUD + 状态机）实现。
 * <p>由 {@link CourseServiceImpl} 委托调用，AdminService 内部不做缓存操作，
 * 缓存失效由 {@link CourseServiceImpl} 在事务提交后统一处理。</p>
 */
@Service
public class CourseAdminServiceImpl implements CourseAdminService {

    private static final Logger LOG = LoggerFactory.getLogger(CourseAdminServiceImpl.class);

    private final CourseRepository courseRepository;
    private final CourseCategoryRepository categoryRepository;
    private final CourseChapterRepository chapterRepository;
    private final UserRepository userRepository;
    private final CourseReviewRepository reviewRepository;
    private final CourseReviewLogRepository reviewLogRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final PluginGrantRepository pluginGrantRepository;
    private final NotificationService notificationService;

    @Value("${upload.base-dir:uploads}")
    private String uploadBaseDir;

    public CourseAdminServiceImpl(CourseRepository courseRepository,
                                  CourseCategoryRepository categoryRepository,
                                  CourseChapterRepository chapterRepository,
                                  UserRepository userRepository,
                                  CourseReviewRepository reviewRepository,
                                  CourseReviewLogRepository reviewLogRepository,
                                  EnrollmentRepository enrollmentRepository,
                                  PluginGrantRepository pluginGrantRepository,
                                  NotificationService notificationService) {
        this.courseRepository = courseRepository;
        this.categoryRepository = categoryRepository;
        this.chapterRepository = chapterRepository;
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
        this.reviewLogRepository = reviewLogRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.pluginGrantRepository = pluginGrantRepository;
        this.notificationService = notificationService;
    }

    /* ================================================================
     *  CUD Methods
     * ================================================================ */

    private void checkPluginGrant(Long teacherId, String courseType) {
        if (courseType == null || "VIDEO".equals(courseType)) return;
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.microcourse.entity.PluginGrant> q =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        q.eq(com.microcourse.entity.PluginGrant::getPluginId, "interactive")
                .eq(com.microcourse.entity.PluginGrant::getGrantType, "TEACHER")
                .eq(com.microcourse.entity.PluginGrant::getGranteeId, teacherId);
        if (pluginGrantRepository.selectCount(q) == 0) {
            throw new BusinessException(ErrorCode.PLUGIN_NO_GRANT);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseVO create(CourseCreateRequest request) {
        // verify foreign references
        if (request.getCategoryId() != null && categoryRepository.selectById(request.getCategoryId()) == null) {
            throw new BusinessException(ErrorCode.COURSE_CATEGORY_NOT_FOUND);
        }
        if (request.getTeacherId() != null && userRepository.selectById(request.getTeacherId()) == null) {
            throw new BusinessException(ErrorCode.COURSE_TEACHER_NOT_FOUND);
        }
        checkPluginGrant(request.getTeacherId(), request.getCourseType());

        Course course = new Course();
        course.setTitle(request.getTitle());
        course.setSubtitle(request.getSubtitle());
        course.setSummary(request.getSummary());
        course.setCoverUrl(request.getCoverUrl());
        course.setCategoryId(request.getCategoryId());
        course.setTeacherId(request.getTeacherId());
        course.setOfferDepartmentId(request.getOfferDepartmentId());
        course.setSemester(request.getSemester());
        course.setCreditHours(request.getCreditHours());
        course.setCourseNature(request.getCourseNature());
        course.setMaxStudents(request.getMaxStudents());
        course.setDifficulty(request.getDifficulty());
        course.setDescription(request.getDescription());
        course.setTags(request.getTags());
        course.setCourseType(request.getCourseType());
        course.setPrice(request.getPrice());
        course.setIsFree(request.getPrice() == null || BigDecimal.ZERO.compareTo(request.getPrice()) >= 0);
        course.setFreeAccessScope(request.getFreeAccessScope());
        course.setFreeDeptIds(request.getFreeDeptIds());
        course.setDiscountScope(request.getDiscountScope());
        course.setDiscountPercent(request.getDiscountPercent());
        course.setStatus(CourseStatus.DRAFT.getCode());
        course.setStudentCount(0);
        course.setAvgRating(BigDecimal.ZERO);

        courseRepository.insert(course);
        LOG.info("课程创建成功, id={}, title={}, operator={}", course.getId(), course.getTitle());
        return convertToVO(course);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseVO update(Long id, CourseUpdateRequest request) {
        Course course = getCourseOrThrow(id);
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        // 已发布课程不可直接编辑（须先下架）
        if (CourseStatus.PUBLISHED.getCode() == course.getStatus()) {
            throw new BusinessException(ErrorCode.COURSE_PUBLISHED_CANNOT_EDIT);
        }

        // apply non-null fields selectively
        if (request.getTitle() != null) course.setTitle(request.getTitle());
        if (request.getSubtitle() != null) course.setSubtitle(request.getSubtitle());
        if (request.getSummary() != null) course.setSummary(request.getSummary());
        if (request.getCoverUrl() != null) course.setCoverUrl(request.getCoverUrl());
        if (request.getCategoryId() != null) {
            if (categoryRepository.selectById(request.getCategoryId()) == null) {
                throw new BusinessException(ErrorCode.COURSE_CATEGORY_NOT_FOUND);
            }
            course.setCategoryId(request.getCategoryId());
        }
        if (request.getTeacherId() != null) {
            if (userRepository.selectById(request.getTeacherId()) == null) {
                throw new BusinessException(ErrorCode.COURSE_TEACHER_NOT_FOUND);
            }
            course.setTeacherId(request.getTeacherId());
        }
        if (request.getOfferDepartmentId() != null) course.setOfferDepartmentId(request.getOfferDepartmentId());
        if (request.getSemester() != null) course.setSemester(request.getSemester());
        if (request.getCreditHours() != null) course.setCreditHours(request.getCreditHours());
        if (request.getCourseNature() != null) course.setCourseNature(request.getCourseNature());
        if (request.getMaxStudents() != null) course.setMaxStudents(request.getMaxStudents());
        if (request.getDifficulty() != null) course.setDifficulty(request.getDifficulty());
        if (request.getDescription() != null) course.setDescription(request.getDescription());
        if (request.getTags() != null) course.setTags(request.getTags());
        if (request.getCourseType() != null) {
            checkPluginGrant(course.getTeacherId(), request.getCourseType());
            course.setCourseType(request.getCourseType());
        }
        if (request.getPrice() != null) {
            course.setPrice(request.getPrice());
            course.setIsFree(BigDecimal.ZERO.compareTo(request.getPrice()) >= 0);
        }
        if (request.getIsFree() != null) course.setIsFree(request.getIsFree());
        if (request.getFreeAccessScope() != null) course.setFreeAccessScope(request.getFreeAccessScope());
        if (request.getFreeDeptIds() != null) course.setFreeDeptIds(request.getFreeDeptIds());
        if (request.getDiscountScope() != null) course.setDiscountScope(request.getDiscountScope());
        if (request.getDiscountPercent() != null) course.setDiscountPercent(request.getDiscountPercent());

        courseRepository.updateById(course);
        LOG.info("课程更新成功, id={}, operator={}", id);
        return convertToVO(course);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Course course = getCourseOrThrow(id);
        Integer currentStatus = course.getStatus() != null ? course.getStatus() : CourseStatus.DRAFT.getCode();
        CourseStatus fromStatus = CourseStatus.fromCode(currentStatus);

        if (fromStatus == CourseStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.COURSE_STATUS_TRANSITION_NOT_ALLOWED, "已归档课程不可操作");
        }

        // TEACHER 只能删除自己的课程
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        // FK 检查：有活跃选课记录时禁止关闭
        long enrollCount = enrollmentRepository.selectCount(
                new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getCourseId, id)
                        .notIn(Enrollment::getEnrollmentStatus, EnrollmentStatus.CANCELLED.getValue(),
                                EnrollmentStatus.WAITLIST.getValue()));
        if (enrollCount > 0) {
            throw new BusinessException(ErrorCode.COURSE_HAS_ENROLLMENTS);
        }

        // DRAFT / REJECTED → CLOSED CAS（非物理删除，@TableLogic 仅用于已归档课程）
        int affected = courseRepository.update(null,
                new LambdaUpdateWrapper<Course>()
                        .eq(Course::getId, id)
                        .eq(Course::getStatus, currentStatus)
                        .set(Course::getStatus, CourseStatus.CLOSED.getCode())
                        .set(Course::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = COALESCE(version, 0) + 1"));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.COURSE_STATUS_TRANSITION_NOT_ALLOWED);
        }
        LOG.info("课程已关闭, id={}", id);
    }

    /* ================================================================
     *  Copy & Cover
     * ================================================================ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseVO copy(Long id) {
        Course original = getCourseOrThrow(id);

        Course course = new Course();
        course.setTitle(original.getTitle() + " - 副本");
        course.setSubtitle(original.getSubtitle());
        course.setSummary(original.getSummary());
        course.setCoverUrl(original.getCoverUrl());
        course.setCategoryId(original.getCategoryId());
        course.setTeacherId(original.getTeacherId());
        course.setOfferDepartmentId(original.getOfferDepartmentId());
        course.setSemester(original.getSemester());
        course.setCreditHours(original.getCreditHours());
        course.setCourseNature(original.getCourseNature());
        course.setMaxStudents(original.getMaxStudents());
        course.setDifficulty(original.getDifficulty());
        course.setDescription(original.getDescription());
        course.setTags(original.getTags());
        course.setCourseType(original.getCourseType());
        course.setPrice(original.getPrice());
        course.setIsFree(original.getIsFree());
        course.setFreeAccessScope(original.getFreeAccessScope());
        course.setFreeDeptIds(original.getFreeDeptIds());
        course.setDiscountScope(original.getDiscountScope());
        course.setDiscountPercent(original.getDiscountPercent());
        course.setStatus(CourseStatus.DRAFT.getCode());
        course.setStudentCount(0);
        course.setAvgRating(BigDecimal.ZERO);

        courseRepository.insert(course);

        // 复制原课程的所有章节
        LambdaQueryWrapper<CourseChapter> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseChapter::getCourseId, id).orderByAsc(CourseChapter::getSortOrder);
        List<CourseChapter> chapters = chapterRepository.selectList(wrapper);
        for (CourseChapter ch : chapters) {
            CourseChapter copyCh = new CourseChapter();
            copyCh.setCourseId(course.getId());
            copyCh.setTitle(ch.getTitle());
            copyCh.setDescription(ch.getDescription());
            copyCh.setSortOrder(ch.getSortOrder());
            copyCh.setChapterType(ch.getChapterType());
            copyCh.setDuration(ch.getDuration());
            copyCh.setLearningObjectives(ch.getLearningObjectives());
            chapterRepository.insert(copyCh);
        }

        LOG.info("课程复制成功, originalId={}, newId={}, operator={}", id, course.getId());
        return convertToVO(course);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseVO updateCover(Long id, MultipartFile file) {
        Course course = getCourseOrThrow(id);

        String originalFilename = file.getOriginalFilename();
        FileUploadUtil.assertSafeFilename(originalFilename);
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String filename = UUID.randomUUID().toString() + ext;
        String coversDir = uploadBaseDir + "/covers";
        try {
            Files.createDirectories(Paths.get(coversDir));
            Path dest = Paths.get(coversDir, filename);
            file.transferTo(dest.toFile());
            course.setCoverUrl("covers/" + filename);
            courseRepository.updateById(course);
        } catch (IOException e) {
            LOG.error("封面文件保存失败, courseId={}", id, e);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "封面文件保存失败");
        }

        LOG.info("课程封面更新成功, id={}, coverUrl={}", id, course.getCoverUrl());
        return convertToVO(course);
    }

    /* ================================================================
     *  Direct Status Transitions (generic)
     * ================================================================ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        Course course = getCourseOrThrow(id);
        CourseStatus current = CourseStatus.fromCode(course.getStatus());
        CourseStatus target = CourseStatus.fromCode(status);
        if (current == null || target == null || !current.canTransitionTo(target)) {
            throw new BusinessException(ErrorCode.COURSE_STATUS_TRANSITION_NOT_ALLOWED);
        }
        course.setStatus(target.getCode());
        courseRepository.updateById(course);
        LOG.info("课程状态更新, id={}, from={}, to={}", id, current, target);
    }

    /* ================================================================
     *  State Machine: Workflow Methods
     * ================================================================ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitForReview(Long id) {
        Course course = getCourseOrThrow(id);
        CourseStatus current = CourseStatus.fromCode(course.getStatus());
        if (current == null || !current.canTransitionTo(CourseStatus.PENDING_REVIEW)) {
            throw new BusinessException(ErrorCode.COURSE_STATUS_TRANSITION_NOT_ALLOWED, "当前状态不允许提交审核");
        }
        // 前置校验：至少有一个章节（Phase A-4 P0-5 约束）
        long chapterCount = chapterRepository.selectCount(
                new LambdaQueryWrapper<CourseChapter>()
                        .eq(CourseChapter::getCourseId, id));
        if (chapterCount == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "请至少添加一个章节再提交审核");
        }
        course.setStatus(CourseStatus.PENDING_REVIEW.getCode());
        course.setRejectReason(null);
        courseRepository.updateById(course);
        LOG.info("课程提交审核, id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        Course course = getCourseOrThrow(id);
        int affected = courseRepository.update(null,
                new LambdaUpdateWrapper<Course>()
                        .eq(Course::getId, id)
                        .eq(Course::getStatus, CourseStatus.PENDING_REVIEW.getCode())
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
                    com.microcourse.enums.NotificationType.COURSE_APPROVED,
                    "课程审核通过", "您的课程《" + course.getTitle() + "》已通过审核", id);
        }
        LOG.info("课程审核通过, id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String reason) {
        Course course = getCourseOrThrow(id);
        String safeReason = com.microcourse.util.XssSanitizer.sanitizePlainText(reason);
        int affected = courseRepository.update(null,
                new LambdaUpdateWrapper<Course>()
                        .eq(Course::getId, id)
                        .eq(Course::getStatus, CourseStatus.PENDING_REVIEW.getCode())
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
                    com.microcourse.enums.NotificationType.COURSE_REJECTED,
                    "课程审核驳回", "您的课程《" + course.getTitle() + "》已被驳回，原因：" + safeReason, id);
        }
        LOG.info("课程审核驳回, id={}, reason={}", id, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        Course course = getCourseOrThrow(id);
        int affected = courseRepository.update(null,
                new LambdaUpdateWrapper<Course>()
                        .eq(Course::getId, id)
                        .in(Course::getStatus, CourseStatus.APPROVED.getCode(), CourseStatus.CLOSED.getCode())
                        .set(Course::getStatus, CourseStatus.PUBLISHED.getCode())
                        .set(Course::getPublishedAt, LocalDateTime.now())
                        .set(Course::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.COURSE_STATUS_TRANSITION_NOT_ALLOWED, "发布失败，请检查课程状态");
        }
        recordReviewLog(id, "PUBLISH", course.getStatus(), CourseStatus.PUBLISHED.getCode(), null);
        LOG.info("课程发布成功, id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unpublish(Long id) {
        Course course = getCourseOrThrow(id);
        CourseStatus current = CourseStatus.fromCode(course.getStatus());
        if (current == null || !current.canTransitionTo(CourseStatus.CLOSED)) {
            throw new BusinessException(ErrorCode.COURSE_STATUS_TRANSITION_NOT_ALLOWED, "当前状态不允许下架");
        }
        course.setStatus(CourseStatus.CLOSED.getCode());
        courseRepository.updateById(course);
        LOG.info("课程下架成功, id={}, operator={}", id);
    }

    /* ================================================================
     *  Private Helpers
     * ================================================================ */

    /**
     * 加载课程，不存在则抛 COURSE_NOT_FOUND。
     */
    private Course getCourseOrThrow(Long id) {
        Course course = courseRepository.selectById(id);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        return course;
    }

    /**
     * 将 Course 实体装配为 CourseVO（单记录场景，内联加载 category/teacher/review 信息）。
     */
    private CourseVO convertToVO(Course course) {
        CourseVO vo = new CourseVO();
        vo.setId(course.getId());
        vo.setTitle(course.getTitle());
        vo.setSubtitle(course.getSubtitle());
        vo.setSummary(course.getSummary());
        vo.setCoverUrl(course.getCoverUrl());
        vo.setCategoryId(course.getCategoryId());
        vo.setTeacherId(course.getTeacherId());
        vo.setOfferDepartmentId(course.getOfferDepartmentId());
        vo.setSemester(course.getSemester());
        vo.setCreditHours(course.getCreditHours());
        vo.setCourseNature(course.getCourseNature());
        vo.setMaxStudents(course.getMaxStudents());
        vo.setDifficulty(course.getDifficulty());
        vo.setStatus(course.getStatus());
        vo.setRejectReason(course.getRejectReason());
        vo.setDescription(course.getDescription());
        vo.setStudentCount(course.getStudentCount());
        vo.setAvgRating(course.getAvgRating());
        vo.setPublishedAt(course.getPublishedAt());
        vo.setCreatedAt(course.getCreatedAt());
        vo.setUpdatedAt(course.getUpdatedAt());
        vo.setVersion(course.getVersion());
        vo.setIsRecommended(course.getIsRecommended());
        vo.setTags(course.getTags());
        vo.setCourseType(course.getCourseType());
        vo.setPrice(course.getPrice());
        vo.setIsFree(course.getIsFree());
        vo.setListPrice(course.getListPrice());
        vo.setFreeAccessScope(course.getFreeAccessScope());
        vo.setFreeAccessScopeLabel(getFreeAccessScopeLabel(course.getFreeAccessScope()));
        vo.setDiscountScope(course.getDiscountScope());
        vo.setDiscountPercent(course.getDiscountPercent());
        vo.setPricingStatus(course.getPricingStatus());

        if (course.getStatus() != null) {
            vo.setStatusText(CourseStatus.getDescription(course.getStatus()));
        }

        if (course.getCategoryId() != null) {
            CourseCategory category = categoryRepository.selectById(course.getCategoryId());
            if (category != null) {
                vo.setCategoryName(category.getName());
            }
        }

        if (course.getTeacherId() != null) {
            User teacher = userRepository.selectById(course.getTeacherId());
            if (teacher != null) {
                vo.setTeacherName(teacher.getRealName());
            }
        }

        vo.setRatingCount((int) reviewRepository.countByCourseId(course.getId()));
        return vo;
    }

    /**
     * 免费范围标签文本，与 CourseQueryServiceImpl 保持一致的字典映射。
     */
    private String getFreeAccessScopeLabel(String scope) {
        if (scope == null) return null;
        switch (scope) {
            case "same_department": return "同院系免费";
            case "same_college":    return "同学院免费";
            case "same_school":     return "同校免费";
            default:                return null;
        }
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
