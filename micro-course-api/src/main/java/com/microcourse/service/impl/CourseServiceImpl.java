package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.*;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseCategory;
import com.microcourse.entity.CourseChapter;
import com.microcourse.entity.CourseReviewLog;
import com.microcourse.entity.User;
import com.microcourse.enums.CourseStatus;
import com.microcourse.enums.UserRole;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.entity.PluginGrant;
import com.microcourse.plugin.PluginRegistry;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.CourseCategoryRepository;
import com.microcourse.repository.CourseReviewRepository;
import com.microcourse.repository.CourseReviewLogRepository;
import com.microcourse.repository.PluginGrantRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.plugin.interactive.mapper.CourseSlideMapper;
import com.microcourse.plugin.interactive.mapper.SlidePageMapper;
import com.microcourse.plugin.interactive.entity.CourseSlide;
import com.microcourse.plugin.interactive.entity.SlidePage;
import com.microcourse.service.CourseService;
import com.microcourse.util.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CourseServiceImpl implements CourseService {

    private final CourseRepository courseRepository;
    private final CourseCategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final CourseChapterRepository chapterRepository;
    private final CourseReviewRepository reviewRepository;
    private final CourseReviewLogRepository reviewLogRepository;
    private final PluginGrantRepository pluginGrantRepository;
    private final PluginRegistry pluginRegistry;
    private final CourseSlideMapper courseSlideMapper;
    private final SlidePageMapper slidePageMapper;

    public CourseServiceImpl(CourseRepository courseRepository,
                             CourseCategoryRepository categoryRepository,
                             UserRepository userRepository,
                             CourseChapterRepository chapterRepository,
                             CourseReviewRepository reviewRepository,
                             CourseReviewLogRepository reviewLogRepository,
                             PluginGrantRepository pluginGrantRepository,
                             PluginRegistry pluginRegistry,
                             CourseSlideMapper courseSlideMapper,
                             SlidePageMapper slidePageMapper) {
        this.courseRepository = courseRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.chapterRepository = chapterRepository;
        this.reviewRepository = reviewRepository;
        this.reviewLogRepository = reviewLogRepository;
        this.pluginGrantRepository = pluginGrantRepository;
        this.pluginRegistry = pluginRegistry;
        this.courseSlideMapper = courseSlideMapper;
        this.slidePageMapper = slidePageMapper;
    }

    /**
     * 记录课程审核日志（CAS 更新成功后调用）
     */
    private void recordReviewLog(Long courseId, String action, Integer previousStatus,
                                 Integer newStatus, String reason) {
        CourseReviewLog log = new CourseReviewLog();
        log.setCourseId(courseId);
        log.setReviewerId(SecurityUtil.getCurrentUserId());
        log.setAction(action);
        log.setPreviousStatus(previousStatus);
        log.setNewStatus(newStatus);
        log.setReason(reason);
        log.setCreatedAt(LocalDateTime.now());
        reviewLogRepository.insert(log);
    }

    @Override
    public PageResult<CourseVO> page(CoursePageQuery query) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        // keyword: 模糊匹配 title 或教师名（通过 teacherId 关联查询 teacher.real_name）
        if (query.getKeyword() != null && !query.getKeyword().isEmpty()) {
            String kw = query.getKeyword()
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
            // 先找出匹配教师名的 teacher IDs
            LambdaQueryWrapper<User> teacherWrapper = new LambdaQueryWrapper<>();
            teacherWrapper.like(User::getRealName, kw);
            List<User> teachers = userRepository.selectList(teacherWrapper);
            List<Long> teacherIds = teachers.stream().map(User::getId).collect(Collectors.toList());

            if (teacherIds.isEmpty()) {
                wrapper.like(Course::getTitle, kw);
            } else {
                wrapper.and(w -> w.like(Course::getTitle, kw)
                        .or(w2 -> w2.in(Course::getTeacherId, teacherIds)));
            }
        }
        if (query.getTitle() != null && !query.getTitle().isEmpty()) {
            wrapper.eq(query.getKeyword() == null || query.getKeyword().isEmpty(),
                    Course::getTitle, query.getTitle());
        }
        if (query.getCategoryId() != null) {
            wrapper.eq(Course::getCategoryId, query.getCategoryId());
        }
        if (query.getTeacherId() != null) {
            wrapper.eq(Course::getTeacherId, query.getTeacherId());
        }
        if (query.getStatus() != null) {
            wrapper.eq(Course::getStatus, query.getStatus());
        }
        if (query.getRecommended() != null) {
            wrapper.eq(Course::getIsRecommended, query.getRecommended());
        }
        if (query.getDifficulty() != null) {
            wrapper.eq(Course::getDifficulty, query.getDifficulty());
        }
        if (query.getCourseType() != null && !query.getCourseType().isEmpty()) {
            wrapper.eq(Course::getCourseType, query.getCourseType());
        }
        // 排序
        String sortBy = query.getSortBy();
        String sortOrder = "asc".equalsIgnoreCase(query.getSortOrder()) ? "asc" : "desc";
        if ("studentCount".equals(sortBy)) {
            if ("asc".equals(sortOrder)) wrapper.orderByAsc(Course::getStudentCount);
            else wrapper.orderByDesc(Course::getStudentCount);
        } else if ("avgRating".equals(sortBy)) {
            if ("asc".equals(sortOrder)) wrapper.orderByAsc(Course::getAvgRating);
            else wrapper.orderByDesc(Course::getAvgRating);
        } else if ("updatedAt".equals(sortBy)) {
            if ("asc".equals(sortOrder)) wrapper.orderByAsc(Course::getUpdatedAt);
            else wrapper.orderByDesc(Course::getUpdatedAt);
        } else {
            // 默认按创建时间倒序
            wrapper.orderByDesc(Course::getCreatedAt);
        }

        IPage<Course> ipage = courseRepository.selectPage(
                new Page<>(query.getPage() + 1, query.getSize()), wrapper);

        // N+1 修复：批量预加载 category 和 teacher
        java.util.Map<Long, CourseCategory> categoryMap = new java.util.HashMap<>();
        java.util.Map<Long, User> teacherMap = new java.util.HashMap<>();

        java.util.Set<Long> categoryIds = ipage.getRecords().stream()
                .map(Course::getCategoryId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Set<Long> teacherIds = ipage.getRecords().stream()
                .map(Course::getTeacherId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        if (!categoryIds.isEmpty()) {
            categoryRepository.selectBatchIds(categoryIds).forEach(c -> categoryMap.put(c.getId(), c));
        }
        if (!teacherIds.isEmpty()) {
            userRepository.selectBatchIds(teacherIds).forEach(u -> teacherMap.put(u.getId(), u));
        }

        final java.util.Map<Long, CourseCategory> finalCategoryMap = categoryMap;
        final java.util.Map<Long, User> finalTeacherMap = teacherMap;

        // Phase 11: 批量预加载评价数量，避免 N+1
        java.util.Map<Long, Long> ratingCountMap = new java.util.HashMap<>();
        if (!ipage.getRecords().isEmpty()) {
            List<Long> courseIds = ipage.getRecords().stream()
                    .map(Course::getId).collect(Collectors.toList());
            List<java.util.Map<String, Object>> countRows = reviewRepository.countByCourseIds(courseIds);
            for (java.util.Map<String, Object> row : countRows) {
                Object courseIdObj = row.get("course_id");
                Object cntObj = row.get("cnt");
                if (courseIdObj != null && cntObj != null) {
                    Long courseId = ((Number) courseIdObj).longValue();
                    Long cnt = ((Number) cntObj).longValue();
                    ratingCountMap.put(courseId, cnt);
                }
            }
        }
        final java.util.Map<Long, Long> finalRatingCountMap = ratingCountMap;

        List<CourseVO> vos = ipage.getRecords().stream()
                .map(course -> convertToVOFromMaps(course, finalCategoryMap, finalTeacherMap, finalRatingCountMap))
                .collect(Collectors.toList());

        PageResult<CourseVO> result = new PageResult<>();
        result.setItems(vos);
        result.setPage(query.getPage());
        result.setSize(query.getSize());
        result.setTotalElements(ipage.getTotal());
        result.setTotalPages(ipage.getPages());
        return result;
    }

    @Override
    public CourseVO getById(Long id) {
        Course course = courseRepository.selectById(id);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        // RES-NEW-2 修复:批量预加载 category/teacher,避免 N+1
        CourseCategory category = course.getCategoryId() != null
                ? categoryRepository.selectById(course.getCategoryId()) : null;
        User teacher = course.getTeacherId() != null
                ? userRepository.selectById(course.getTeacherId()) : null;
        CourseVO vo = convertToVO(course, category, teacher, reviewRepository.countByCourseId(course.getId()));

        // RES-NEW-3 修复:章节列表限制 200 条,防止超大课程返回数 MB payload
        LambdaQueryWrapper<CourseChapter> chapterWrapper = new LambdaQueryWrapper<>();
        chapterWrapper.eq(CourseChapter::getCourseId, id)
                .orderByAsc(CourseChapter::getSortOrder)
                .last("LIMIT 200");
        List<CourseChapter> chapters = chapterRepository.selectList(chapterWrapper);
        vo.setChapters(chapters.stream().map(this::convertChapterToVO).collect(Collectors.toList()));

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseVO create(CourseCreateRequest request) {
        // Validate category exists
        if (categoryRepository.selectById(request.getCategoryId()) == null) {
            throw new BusinessException(ErrorCode.COURSE_CATEGORY_NOT_FOUND);
        }

        // SECURITY: TEACHER 只能为自己创建课程，ADMIN 可指定任意教师
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (!SecurityUtil.isAdmin()) {
            request.setTeacherId(currentUserId);
        }

        // Validate teacher exists and has TEACHER role
        User teacher = userRepository.selectById(request.getTeacherId());
        if (teacher == null) {
            throw new BusinessException(ErrorCode.COURSE_TEACHER_NOT_FOUND);
        }
        if (teacher.getRole() != UserRole.TEACHER) {
            throw new BusinessException(ErrorCode.COURSE_TEACHER_NOT_FOUND);
        }

        String courseType = request.getCourseType() != null ? request.getCourseType() : "VIDEO";
        if (!"VIDEO".equals(courseType)) {
            if (!pluginRegistry.isEnabled(courseType)) {
                throw new BusinessException(ErrorCode.PLUGIN_NOT_ENABLED);
            }
            if (!hasPluginGrant(courseType, request.getTeacherId())) {
                throw new BusinessException(ErrorCode.PLUGIN_NO_GRANT);
            }
        }

        Course course = new Course();
        course.setTitle(request.getTitle());
        course.setCourseType(courseType);
        course.setPrice(request.getPrice());
        course.setIsFree(request.getPrice() == null || request.getPrice().compareTo(java.math.BigDecimal.ZERO) == 0);
        course.setCategoryId(request.getCategoryId());
        course.setTeacherId(request.getTeacherId());
        course.setSubtitle(request.getSubtitle());
        course.setSummary(request.getSummary());
        course.setCoverUrl(request.getCoverUrl());
        course.setOfferDepartmentId(request.getOfferDepartmentId());
        course.setSemester(request.getSemester());
        course.setCreditHours(request.getCreditHours());
        course.setCourseNature(request.getCourseNature());
        course.setMaxStudents(request.getMaxStudents());
        course.setDifficulty(request.getDifficulty());
        course.setDescription(request.getDescription());
        course.setTags(request.getTags());
        course.setStatus(CourseStatus.DRAFT.getCode());
        course.setCreatedAt(LocalDateTime.now());
        course.setUpdatedAt(LocalDateTime.now());
        course.setVersion(0);

        courseRepository.insert(course);
        return convertToVO(course, null, null, 0L);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseVO update(Long id, CourseUpdateRequest request) {
        Course course = courseRepository.selectById(id);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        // Owner check: only course teacher or ADMIN can update
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        // Published course cannot be edited directly
        if (course.getStatus() != null && course.getStatus() == CourseStatus.PUBLISHED.getCode()) {
            throw new BusinessException(ErrorCode.COURSE_PUBLISHED_CANNOT_EDIT);
        }

        // Partial update
        if (request.getTitle() != null) course.setTitle(request.getTitle());
        if (request.getCategoryId() != null) {
            if (categoryRepository.selectById(request.getCategoryId()) == null) {
                throw new BusinessException(ErrorCode.COURSE_CATEGORY_NOT_FOUND);
            }
            course.setCategoryId(request.getCategoryId());
        }
        if (request.getTeacherId() != null) {
            User teacher = userRepository.selectById(request.getTeacherId());
            if (teacher == null || teacher.getRole() != UserRole.TEACHER) {
                throw new BusinessException(ErrorCode.COURSE_TEACHER_NOT_FOUND);
            }
            course.setTeacherId(request.getTeacherId());
        }
        if (request.getSubtitle() != null) course.setSubtitle(request.getSubtitle());
        if (request.getSummary() != null) course.setSummary(request.getSummary());
        if (request.getCoverUrl() != null) course.setCoverUrl(request.getCoverUrl());
        if (request.getOfferDepartmentId() != null) course.setOfferDepartmentId(request.getOfferDepartmentId());
        if (request.getSemester() != null) course.setSemester(request.getSemester());
        if (request.getCreditHours() != null) course.setCreditHours(request.getCreditHours());
        if (request.getCourseNature() != null) course.setCourseNature(request.getCourseNature());
        if (request.getMaxStudents() != null) course.setMaxStudents(request.getMaxStudents());
        if (request.getDifficulty() != null) course.setDifficulty(request.getDifficulty());
        if (request.getDescription() != null) course.setDescription(request.getDescription());
        if (request.getTags() != null) course.setTags(request.getTags());
        if (request.getCourseType() != null) {
            // courseType 变更时校验插件授权
            if (!request.getCourseType().equals(course.getCourseType()) && !"VIDEO".equals(request.getCourseType())) {
                if (!pluginRegistry.isEnabled(request.getCourseType())) {
                    throw new BusinessException(ErrorCode.PLUGIN_NOT_ENABLED);
                }
                if (!hasPluginGrant(request.getCourseType(), course.getTeacherId())) {
                    throw new BusinessException(ErrorCode.PLUGIN_NO_GRANT);
                }
            }
            course.setCourseType(request.getCourseType());
        }
        if (request.getPrice() != null) { course.setPrice(request.getPrice()); course.setIsFree(request.getPrice().compareTo(java.math.BigDecimal.ZERO) == 0); }
        if (request.getIsFree() != null) course.setIsFree(request.getIsFree());

        course.setUpdatedAt(LocalDateTime.now());

        courseRepository.updateById(course);
        return convertToVO(course, null, null, reviewRepository.countByCourseId(course.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        Course course = courseRepository.selectById(id);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }

        CourseStatus newStatus = CourseStatus.fromCode(status);
        if (newStatus == null) {
            throw new BusinessException(ErrorCode.COURSE_INVALID_STATUS);
        }

        Integer currentStatus = course.getStatus();

        if (!isValidTransition(currentStatus, status)) {
            throw new BusinessException(ErrorCode.COURSE_STATUS_TRANSITION_NOT_ALLOWED);
        }

        course.setStatus(status);
        course.setUpdatedAt(LocalDateTime.now());
        course.setVersion(course.getVersion() == null ? 1 : course.getVersion() + 1);

        if (status == CourseStatus.PUBLISHED.getCode()) {
            course.setPublishedAt(LocalDateTime.now());
        }

        courseRepository.updateById(course);
        recordReviewLog(id, "UPDATE", currentStatus, status, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitForReview(Long id) {
        // R1-AUTH-002 修复:CAS 重构时需保留所有权校验,防止任何 TEACHER 提交他人课程
        Course course = courseRepository.selectById(id);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        // DRAFT(0) / REJECTED(3) → PENDING_REVIEW(1) CAS 推进(CON-NEW-3 修复:防止并发双提交)
        int affected = courseRepository.update(null,
                new LambdaUpdateWrapper<Course>()
                        .eq(Course::getId, id)
                        .and(w -> w.eq(Course::getStatus, CourseStatus.DRAFT.getCode())
                                .or()
                                .eq(Course::getStatus, CourseStatus.REJECTED.getCode()))
                        .set(Course::getStatus, CourseStatus.PENDING_REVIEW.getCode())
                        .set(Course::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = COALESCE(version, 0) + 1"));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.COURSE_STATUS_TRANSITION_NOT_ALLOWED);
        }
        recordReviewLog(id, "SUBMIT", course.getStatus(),
                CourseStatus.PENDING_REVIEW.getCode(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        // R1-API-004 修复:保留 COURSE_NOT_FOUND 错误码区分
        if (courseRepository.selectById(id) == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        // Admin or Academic check
        if (!SecurityUtil.isAdminOrAcademic()) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        // PENDING_REVIEW(1) → APPROVED(2) CAS
        int affected = courseRepository.update(null,
                new LambdaUpdateWrapper<Course>()
                        .eq(Course::getId, id)
                        .eq(Course::getStatus, CourseStatus.PENDING_REVIEW.getCode())
                        .set(Course::getStatus, CourseStatus.APPROVED.getCode())
                        .set(Course::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = COALESCE(version, 0) + 1"));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.COURSE_STATUS_TRANSITION_NOT_ALLOWED);
        }
        recordReviewLog(id, "APPROVE", CourseStatus.PENDING_REVIEW.getCode(),
                CourseStatus.APPROVED.getCode(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String reason) {
        // R1-API-004 修复:保留 COURSE_NOT_FOUND 错误码区分
        if (courseRepository.selectById(id) == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        if (!SecurityUtil.isAdminOrAcademic()) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        // PENDING_REVIEW(1) → REJECTED(3) CAS
        int affected = courseRepository.update(null,
                new LambdaUpdateWrapper<Course>()
                        .eq(Course::getId, id)
                        .eq(Course::getStatus, CourseStatus.PENDING_REVIEW.getCode())
                        .set(Course::getStatus, CourseStatus.REJECTED.getCode())
                        .set(Course::getRejectReason, reason)
                        .set(Course::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = COALESCE(version, 0) + 1"));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.COURSE_STATUS_TRANSITION_NOT_ALLOWED);
        }
        recordReviewLog(id, "REJECT", CourseStatus.PENDING_REVIEW.getCode(),
                CourseStatus.REJECTED.getCode(), reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        // R1-API-004 修复:保留 COURSE_NOT_FOUND 错误码区分
        if (courseRepository.selectById(id) == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        if (!SecurityUtil.isAdminOrAcademic()) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        // APPROVED(2) → PUBLISHED(4) CAS
        int affected = courseRepository.update(null,
                new LambdaUpdateWrapper<Course>()
                        .eq(Course::getId, id)
                        .eq(Course::getStatus, CourseStatus.APPROVED.getCode())
                        .set(Course::getStatus, CourseStatus.PUBLISHED.getCode())
                        .set(Course::getPublishedAt, LocalDateTime.now())
                        .set(Course::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = COALESCE(version, 0) + 1"));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.COURSE_STATUS_TRANSITION_NOT_ALLOWED);
        }
        recordReviewLog(id, "PUBLISH", CourseStatus.APPROVED.getCode(),
                CourseStatus.PUBLISHED.getCode(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Course course = courseRepository.selectById(id);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }

        Integer currentStatus = course.getStatus() != null ? course.getStatus() : CourseStatus.DRAFT.getCode();

        // DRAFT / REJECTED / ARCHIVED → CLOSED: 直接标记,不经 isValidTransition 校验
        if (currentStatus == CourseStatus.DRAFT.getCode()
                || currentStatus == CourseStatus.REJECTED.getCode()
                || currentStatus == CourseStatus.ARCHIVED.getCode()) {
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
        } else {
            // PUBLISHED → CLOSED 等合法转换走 updateStatus
            updateStatus(id, CourseStatus.CLOSED.getCode());
        }

        LambdaQueryWrapper<CourseChapter> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseChapter::getCourseId, id);
        chapterRepository.delete(wrapper);

        LambdaQueryWrapper<CourseSlide> slideWrapper = new LambdaQueryWrapper<>();
        slideWrapper.eq(CourseSlide::getCourseId, id);
        CourseSlide slide = courseSlideMapper.selectOne(slideWrapper);
        if (slide != null) {
            LambdaQueryWrapper<SlidePage> pageWrapper = new LambdaQueryWrapper<>();
            pageWrapper.eq(SlidePage::getSlideId, slide.getId());
            slidePageMapper.delete(pageWrapper);
            courseSlideMapper.deleteById(slide.getId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseVO copy(Long id) {
        Course source = courseRepository.selectById(id);
        if (source == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        // 权限检查：只有课程教师本人或 ADMIN 可以复制
        if (!SecurityUtil.isOwnerOrAdmin(source.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        // 创建新课程副本（基本信息）
        Course newCourse = new Course();
        newCourse.setTitle("复制: " + source.getTitle());
        newCourse.setCategoryId(source.getCategoryId());
        newCourse.setTeacherId(source.getTeacherId());
        newCourse.setSubtitle(source.getSubtitle());
        newCourse.setSummary(source.getSummary());
        newCourse.setCoverUrl(source.getCoverUrl());
        newCourse.setOfferDepartmentId(source.getOfferDepartmentId());
        newCourse.setSemester(source.getSemester());
        newCourse.setCreditHours(source.getCreditHours());
        newCourse.setCourseNature(source.getCourseNature());
        newCourse.setMaxStudents(source.getMaxStudents());
        newCourse.setDifficulty(source.getDifficulty());
        newCourse.setDescription(source.getDescription());
        newCourse.setTags(source.getTags());
        newCourse.setCourseType(source.getCourseType());
        newCourse.setPrice(source.getPrice());
        newCourse.setIsFree(source.getIsFree());
        newCourse.setStatus(CourseStatus.DRAFT.getCode());
        newCourse.setCreatedAt(LocalDateTime.now());
        newCourse.setUpdatedAt(LocalDateTime.now());
        newCourse.setVersion(0);

        courseRepository.insert(newCourse);

        // 复制章节结构（不含视频文件，只复制章节元数据）
        LambdaQueryWrapper<CourseChapter> chapterWrapper = new LambdaQueryWrapper<>();
        chapterWrapper.eq(CourseChapter::getCourseId, id)
                .orderByAsc(CourseChapter::getSortOrder);
        List<CourseChapter> sourceChapters = chapterRepository.selectList(chapterWrapper);

        for (CourseChapter srcChapter : sourceChapters) {
            CourseChapter newChapter = new CourseChapter();
            newChapter.setCourseId(newCourse.getId());
            newChapter.setTitle(srcChapter.getTitle());
            newChapter.setDescription(srcChapter.getDescription());
            newChapter.setSortOrder(srcChapter.getSortOrder());
            newChapter.setChapterType(srcChapter.getChapterType());
            newChapter.setDuration(0); // 不复制视频时长，重置为0
            newChapter.setCreatedAt(LocalDateTime.now());
            newChapter.setUpdatedAt(LocalDateTime.now());
            newChapter.setVersion(0);
            chapterRepository.insert(newChapter);
        }

        return convertToVO(newCourse);
    }

    private CourseVO convertToVO(Course course) {
        return convertToVO(course, null, null, 0L);
    }

    /**
     * RES-NEW-2 修复:接受预加载的 category/teacher,避免 N+1
     */
    private CourseVO convertToVO(Course course, CourseCategory preloadedCategory, User preloadedTeacher,
                                Long ratingCount) {
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
        vo.setRatingCount(ratingCount != null ? ratingCount.intValue() : 0);
        vo.setPublishedAt(course.getPublishedAt());
        vo.setCreatedAt(course.getCreatedAt());
        vo.setUpdatedAt(course.getUpdatedAt());
        vo.setVersion(course.getVersion());
        vo.setIsRecommended(course.getIsRecommended());
        vo.setCourseType(course.getCourseType());
        vo.setPrice(course.getPrice());
        vo.setIsFree(course.getIsFree());

        if (course.getStatus() != null) {
            vo.setStatusText(CourseStatus.getDescription(course.getStatus()));
        }

        // Category name — 优先用预加载对象,fallback selectById
        if (course.getCategoryId() != null) {
            CourseCategory category = preloadedCategory != null ? preloadedCategory
                    : categoryRepository.selectById(course.getCategoryId());
            if (category != null) {
                vo.setCategoryName(category.getName());
            }
        }

        // Teacher name — 优先用预加载对象,fallback selectById
        if (course.getTeacherId() != null) {
            User teacher = preloadedTeacher != null ? preloadedTeacher
                    : userRepository.selectById(course.getTeacherId());
            if (teacher != null) {
                vo.setTeacherName(teacher.getRealName());
            }
        }

        return vo;
    }

    private CourseVO convertToVOFromMaps(Course course, java.util.Map<Long, CourseCategory> categoryMap,
                                        java.util.Map<Long, User> teacherMap,
                                        java.util.Map<Long, Long> ratingCountMap) {
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
        vo.setRatingCount(ratingCountMap.getOrDefault(course.getId(), 0L).intValue());
        vo.setPublishedAt(course.getPublishedAt());
        vo.setCreatedAt(course.getCreatedAt());
        vo.setUpdatedAt(course.getUpdatedAt());
        vo.setVersion(course.getVersion());
        vo.setIsRecommended(course.getIsRecommended());
        vo.setCourseType(course.getCourseType());
        vo.setPrice(course.getPrice());
        vo.setIsFree(course.getIsFree());

        if (course.getStatus() != null) {
            vo.setStatusText(CourseStatus.getDescription(course.getStatus()));
        }

        // Load category name（使用预加载的 Map）
        if (course.getCategoryId() != null) {
            CourseCategory category = categoryMap.get(course.getCategoryId());
            if (category != null) {
                vo.setCategoryName(category.getName());
            }
        }

        // Load teacher name（使用预加载的 Map）
        if (course.getTeacherId() != null) {
            User teacher = teacherMap.get(course.getTeacherId());
            if (teacher != null) {
                vo.setTeacherName(teacher.getRealName());
            }
        }

        return vo;
    }

    private boolean isValidTransition(Integer from, Integer to) {
        int f = from != null ? from : CourseStatus.DRAFT.getCode();
        // DRAFT → PENDING_REVIEW
        if (f == CourseStatus.DRAFT.getCode() && to == CourseStatus.PENDING_REVIEW.getCode()) return true;
        // PENDING_REVIEW → APPROVED or REJECTED
        if (f == CourseStatus.PENDING_REVIEW.getCode() && (to == CourseStatus.APPROVED.getCode() || to == CourseStatus.REJECTED.getCode())) return true;
        // APPROVED → PUBLISHED
        if (f == CourseStatus.APPROVED.getCode() && to == CourseStatus.PUBLISHED.getCode()) return true;
        // PUBLISHED → CLOSED
        if (f == CourseStatus.PUBLISHED.getCode() && to == CourseStatus.CLOSED.getCode()) return true;
        // CLOSED → ARCHIVED or DRAFT
        if (f == CourseStatus.CLOSED.getCode() && (to == CourseStatus.ARCHIVED.getCode() || to == CourseStatus.DRAFT.getCode())) return true;
        // REJECTED → DRAFT or PENDING_REVIEW（P1#6 修复：教师被驳回后可重新提交审核）
        if (f == CourseStatus.REJECTED.getCode()
                && (to == CourseStatus.DRAFT.getCode() || to == CourseStatus.PENDING_REVIEW.getCode())) return true;
        return false;
    }

    private boolean hasPluginGrant(String pluginId, Long teacherId) {
        if (SecurityUtil.isAdmin()) {
            return true;
        }
        LambdaQueryWrapper<PluginGrant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PluginGrant::getPluginId, pluginId)
                .eq(PluginGrant::getGrantType, "TEACHER")
                .eq(PluginGrant::getGranteeId, teacherId);
        if (pluginGrantRepository.selectCount(wrapper) > 0) {
            return true;
        }
        User teacher = userRepository.selectById(teacherId);
        if (teacher != null && teacher.getDepartmentId() != null) {
            LambdaQueryWrapper<PluginGrant> deptWrapper = new LambdaQueryWrapper<>();
            deptWrapper.eq(PluginGrant::getPluginId, pluginId)
                    .eq(PluginGrant::getGrantType, "DEPARTMENT")
                    .eq(PluginGrant::getGranteeId, teacher.getDepartmentId());
            if (pluginGrantRepository.selectCount(deptWrapper) > 0) {
                return true;
            }
        }
        return false;
    }

    private ChapterVO convertChapterToVO(CourseChapter chapter) {
        ChapterVO vo = new ChapterVO();
        vo.setId(chapter.getId());
        vo.setCourseId(chapter.getCourseId());
        vo.setTitle(chapter.getTitle());
        vo.setDescription(chapter.getDescription());
        vo.setSortOrder(chapter.getSortOrder());
        vo.setChapterType(chapter.getChapterType());
        vo.setDuration(chapter.getDuration());
        vo.setCreatedAt(chapter.getCreatedAt());
        vo.setUpdatedAt(chapter.getUpdatedAt());
        vo.setVersion(chapter.getVersion());
        vo.setLearningObjectives(chapter.getLearningObjectives());
        // 从 learningObjectives 提取 keyConcepts（按换行/逗号/分号分割）
        if (chapter.getLearningObjectives() != null && !chapter.getLearningObjectives().isEmpty()) {
            vo.setKeyConcepts(Arrays.stream(chapter.getLearningObjectives().split("[\\n,;]"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList()));
        }
        // exercises 留空,等待 Phase 6 练习模块实现
        return vo;
    }
}