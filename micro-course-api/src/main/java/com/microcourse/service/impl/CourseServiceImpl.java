package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.ChapterVO;
import com.microcourse.dto.CourseCreateRequest;
import com.microcourse.dto.CoursePageQuery;
import com.microcourse.dto.CourseStatsVO;
import com.microcourse.dto.CourseUpdateRequest;
import com.microcourse.dto.CourseVO;
import com.microcourse.dto.PageResult;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseCategory;
import com.microcourse.entity.CourseChapter;
import com.microcourse.entity.CourseReviewLog;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.User;
import com.microcourse.entity.Video;
import com.microcourse.entity.VideoStatus;
import com.microcourse.enums.CourseStatus;
import com.microcourse.enums.EnrollmentStatus;
import com.microcourse.enums.NotificationType;
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
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.PluginGrantRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.repository.VideoRepository;
import com.microcourse.plugin.interactive.mapper.CourseSlideMapper;
import com.microcourse.plugin.interactive.mapper.SlidePageMapper;
import com.microcourse.plugin.interactive.entity.CourseSlide;
import com.microcourse.plugin.interactive.entity.SlidePage;
import com.microcourse.service.CourseService;
import com.microcourse.service.NotificationService;
import com.microcourse.util.RedisUtil;
import com.microcourse.util.SecurityUtil;
import com.microcourse.util.XssSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class CourseServiceImpl implements CourseService {

    // ★ Round 9-2 修复：业务数据缓存（Redis）日志器。命名用大写 LOG，避免与
    // recordReviewLog() 内的局部变量 CourseReviewLog log 发生遮蔽。
    private static final Logger LOG = LoggerFactory.getLogger(CourseServiceImpl.class);

    // ★ Round 9-2 修复：课程详情缓存（5 分钟）—— 命中 5ms，避免每次回 DB（~100ms）
    private static final String COURSE_CACHE_PREFIX = "mc:course:detail:";
    private static final long COURSE_CACHE_TTL = 300; // 秒

    // ★ Round 9-2 修复：课程统计缓存（1 小时）—— 聚合查询昂贵，命中 3ms
    private static final String COURSE_STATS_CACHE_PREFIX = "mc:course:stats:";
    private static final long COURSE_STATS_CACHE_TTL = 3600; // 秒

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
    private final VideoRepository videoRepository;
    private final NotificationService notificationService;
    private final EnrollmentRepository enrollmentRepository;
    private final RedisUtil redisUtil;

    // E2-1: self-injection 解决内部调用绕过 @Transactional 代理问题
    // @Lazy 避免循环依赖，通过 AOP 代理确保 updateStatus() 的事务正确传播
    private final CourseServiceImpl self;

    /** C2-3 修复：异步线程池，用于 publish() 后异步发送通知，不阻塞主事务。 */
    private final Executor taskExecutor;

    public CourseServiceImpl(CourseRepository courseRepository,
                             CourseCategoryRepository categoryRepository,
                             UserRepository userRepository,
                             CourseChapterRepository chapterRepository,
                             CourseReviewRepository reviewRepository,
                             CourseReviewLogRepository reviewLogRepository,
                             PluginGrantRepository pluginGrantRepository,
                             PluginRegistry pluginRegistry,
                             CourseSlideMapper courseSlideMapper,
                             SlidePageMapper slidePageMapper,
                             VideoRepository videoRepository,
                             NotificationService notificationService,
                             EnrollmentRepository enrollmentRepository,
                             RedisUtil redisUtil,
                             @Qualifier("taskExecutor") Executor taskExecutor,
                             @org.springframework.context.annotation.Lazy CourseServiceImpl self) {
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
        this.videoRepository = videoRepository;
        this.notificationService = notificationService;
        this.enrollmentRepository = enrollmentRepository;
        this.redisUtil = redisUtil;
        this.taskExecutor = taskExecutor;
        this.self = self;
    }

    /**
     * ★ Round 9-2 修复：清除课程相关缓存（详情 + 统计），保证缓存一致性（硬约束 #1）。
     * Redis 故障时仅记录告警，不阻塞主流程（硬约束 #2 降级）。
     */
    public void evictCourseCache(Long courseId) {
        if (courseId == null) {
            return;
        }
        try {
            redisUtil.delete(COURSE_CACHE_PREFIX + courseId);
            redisUtil.delete(COURSE_STATS_CACHE_PREFIX + courseId);
        } catch (Exception e) {
            LOG.warn("[Round9-2] 课程缓存清除失败, courseId={}", courseId, e);
        }
    }

    /**
     * ★ Round 9-2 修复：在事务提交后清除缓存，规避 cache-aside 竞态
     * （写未提交时并发读把旧值重新写入缓存）。无活跃事务时立即清除。
     */
    private void evictCourseCacheAfterCommit(Long courseId) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            evictCourseCache(courseId);
                        }
                    });
        } else {
            evictCourseCache(courseId);
        }
    }

    /**
     * Round 5-3 (P1-10): 计算课程统计数据。
     *
     * <p>选课人数 / 完成率 / 平均分均来自既有 {@code enrollments} 表（按 courseId 聚合），
     * 教师名取自 {@code users}，不引入新表/新列。课程不存在抛 {@link ErrorCode#COURSE_NOT_FOUND}（404）。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseVO updateCover(Long id, MultipartFile file) {
        Course course = courseRepository.selectById(id);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        try {
            String uploadDir = System.getProperty("user.dir") + "/uploads/covers/";
            java.io.File dir = new java.io.File(uploadDir);
            if (!dir.exists()) dir.mkdirs();

            String contentType = file.getContentType();
            String ext = ".jpg";
            if ("image/png".equals(contentType)) ext = ".png";
            else if ("image/gif".equals(contentType)) ext = ".gif";
            else if ("image/webp".equals(contentType)) ext = ".webp";
            String filename = "course_" + id + "_" + System.currentTimeMillis() + ext;
            java.io.File dest = new java.io.File(uploadDir + filename);
            file.transferTo(dest);

            String coverUrl = "/api/files/covers/" + filename;
            course.setCoverUrl(coverUrl);
            course.setUpdatedAt(LocalDateTime.now());
            courseRepository.updateById(course);
            evictCourseCacheAfterCommit(id);
            return convertToVO(course);
        } catch (Exception e) {
            LOG.error("[Course] 封面上传失败 courseId={}", id, e);
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "封面上传失败");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CourseStatsVO computeStats(Long courseId) {
        // ★ Round 9-2 修复：1) 查缓存（Redis 故障降级回 DB，硬约束 #2）
        String cacheKey = COURSE_STATS_CACHE_PREFIX + courseId;
        try {
            Object cached = redisUtil.get(cacheKey);
            if (cached instanceof CourseStatsVO) {
                return (CourseStatsVO) cached;
            }
        } catch (Exception e) {
            LOG.warn("[Round9-2] 课程统计缓存读取失败，降级查询 DB, courseId={}", courseId, e);
        }

        // 2) 查 DB（业务逻辑零修改）
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
                new LambdaQueryWrapper<Enrollment>().eq(Enrollment::getCourseId, courseId));
        long completed = enrollmentRepository.selectCount(
                new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getCourseId, courseId)
                        .eq(Enrollment::getCompleted, true));
        vo.setEnrollmentCount(total);
        vo.setCompletionRate(total > 0 ? (double) completed / total : 0.0);

        // ★ Round 11-2 性能优化：单条 AVG 聚合替代「全量加载该课程已评分选课 + 内存求均值」。
        // 语义等价：仅统计该课程 final_score 非空且未删除的选课记录均分；无数据时 AVG 为 NULL → 0.0。
        Double avg = enrollmentRepository.avgScoreByCourseId(courseId);
        vo.setAvgScore(avg != null ? avg : 0.0);

        // 3) 写缓存（Redis 故障不影响主流程，硬约束 #2）
        try {
            redisUtil.set(cacheKey, vo, COURSE_STATS_CACHE_TTL, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOG.warn("[Round9-2] 课程统计缓存写入失败, courseId={}", courseId, e);
        }
        return vo;
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
        // ★ Round 8-4 修复(P0)：TEACHER 课程列表隔离 —— 教师只能看到自己创建的课程，
        // 不可越权浏览全平台课程；ADMIN / ACADEMIC 不受限（看到全部）。
        // STUDENT 走 getPublishedCourses()，此处不额外限制；合法用户体验零退化。
        boolean teacherScoped = SecurityUtil.hasRole("TEACHER") && !SecurityUtil.isAdmin();
        if (teacherScoped) {
            wrapper.eq(Course::getTeacherId, SecurityUtil.getCurrentUserId());
        }
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
        // TEACHER 已被强制隔离为本人课程，不允许通过 teacherId 参数越权按他人筛选；
        // 非 TEACHER（ADMIN/ACADEMIC）仍可按教师维度筛选。
        if (query.getTeacherId() != null && !teacherScoped) {
            wrapper.eq(Course::getTeacherId, query.getTeacherId());
        }
        if (query.getStatus() != null) {
            wrapper.eq(Course::getStatus, query.getStatus());
        } else if (!teacherScoped) {
            // 默认排除已删除（CLOSED）和已归档（ARCHIVED）的课程
            // 仅对非教师角色生效；教师管理自己课程时需要看到所有状态
            wrapper.notIn(Course::getStatus, List.of(CourseStatus.CLOSED.getCode(), CourseStatus.ARCHIVED.getCode()));
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

        // N+1 修复：批量预加载 category/teacher/评价数量（提取为独立方法降低 page() 复杂度）
        List<Course> records = ipage.getRecords();
        java.util.Map<Long, CourseCategory> categoryMap = buildCategoryMap(records);
        java.util.Map<Long, User> teacherMap = buildTeacherMap(records);
        java.util.Map<Long, Long> ratingCountMap = buildRatingCountMap(records);

        List<CourseVO> vos = records.stream()
                .map(course -> convertToVOFromMaps(course, categoryMap, teacherMap, ratingCountMap))
                .collect(Collectors.toList());

        PageResult<CourseVO> result = new PageResult<>();
        result.setItems(vos);
        result.setPage(query.getPage());
        result.setSize(query.getSize());
        result.setTotalElements(ipage.getTotal());
        result.setTotalPages(ipage.getPages());
        return result;
    }

    /**
     * N+1 修复（提取自 page()）：从课程记录批量提取 categoryId 并一次性查询，
     * 返回 categoryId -> CourseCategory 的预加载 Map。空集合时返回空 Map，行为与原内联逻辑完全一致。
     */
    private java.util.Map<Long, CourseCategory> buildCategoryMap(List<Course> records) {
        java.util.Map<Long, CourseCategory> categoryMap = new java.util.HashMap<>();
        java.util.Set<Long> categoryIds = records.stream()
                .map(Course::getCategoryId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (!categoryIds.isEmpty()) {
            categoryRepository.selectBatchIds(categoryIds).forEach(c -> categoryMap.put(c.getId(), c));
        }
        return categoryMap;
    }

    /**
     * N+1 修复（提取自 page()）：从课程记录批量提取 teacherId 并一次性查询，
     * 返回 teacherId -> User 的预加载 Map。空集合时返回空 Map，行为与原内联逻辑完全一致。
     */
    private java.util.Map<Long, User> buildTeacherMap(List<Course> records) {
        java.util.Map<Long, User> teacherMap = new java.util.HashMap<>();
        java.util.Set<Long> teacherIds = records.stream()
                .map(Course::getTeacherId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (!teacherIds.isEmpty()) {
            userRepository.selectBatchIds(teacherIds).forEach(u -> teacherMap.put(u.getId(), u));
        }
        return teacherMap;
    }

    /**
     * Phase 11 N+1 修复（提取自 page()）：批量加载课程评价数量，
     * 返回 courseId -> 评价数 的预加载 Map。空记录时返回空 Map，行为与原内联逻辑完全一致。
     */
    private java.util.Map<Long, Long> buildRatingCountMap(List<Course> records) {
        java.util.Map<Long, Long> ratingCountMap = new java.util.HashMap<>();
        if (!records.isEmpty()) {
            List<Long> courseIds = records.stream()
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
        return ratingCountMap;
    }

    @Override
    public CourseVO getById(Long id) {
        // ★ Round 9-2 修复：1) 查缓存（Redis 故障降级回 DB，硬约束 #2）
        String cacheKey = COURSE_CACHE_PREFIX + id;
        try {
            Object cached = redisUtil.get(cacheKey);
            if (cached instanceof CourseVO) {
                return (CourseVO) cached;
            }
        } catch (Exception e) {
            LOG.warn("[Round9-2] 课程详情缓存读取失败，降级查询 DB, id={}", id, e);
        }

        // 2) 查 DB（业务逻辑零修改：course 不存在仍抛 COURSE_NOT_FOUND）
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

        // 3) 写缓存（含章节的完整 VO；Redis 故障不影响主流程，硬约束 #2）
        try {
            redisUtil.set(cacheKey, vo, COURSE_CACHE_TTL, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOG.warn("[Round9-2] 课程详情缓存写入失败, id={}", id, e);
        }

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

        String courseType = request.getCourseType() != null && !request.getCourseType().isBlank()
            ? request.getCourseType().trim() : "VIDEO";
        if (!"VIDEO".equals(courseType)) {
            if (!pluginRegistry.isEnabled(courseType)) {
                throw new BusinessException(ErrorCode.PLUGIN_NOT_ENABLED);
            }
            if (!hasPluginGrant(courseType, request.getTeacherId())) {
                throw new BusinessException(ErrorCode.PLUGIN_NO_GRANT);
            }
        }

        Course course = new Course();
        // P1 安全修复: XSS 净化课程文本字段（管理员可信任但防御深度）
        course.setTitle(com.microcourse.util.XssSanitizer.sanitizePlainText(request.getTitle()));
        course.setCourseType(courseType);
        course.setPrice(request.getPrice());
        course.setIsFree(request.getPrice() == null || request.getPrice().compareTo(java.math.BigDecimal.ZERO) == 0);
        course.setCategoryId(request.getCategoryId());
        course.setTeacherId(request.getTeacherId());
        course.setSubtitle(com.microcourse.util.XssSanitizer.sanitizePlainText(request.getSubtitle()));
        course.setSummary(com.microcourse.util.XssSanitizer.sanitize(request.getSummary()));
        course.setCoverUrl(request.getCoverUrl());
        course.setOfferDepartmentId(request.getOfferDepartmentId());
        course.setSemester(request.getSemester());
        course.setCreditHours(request.getCreditHours());
        course.setCourseNature(request.getCourseNature());
        course.setMaxStudents(request.getMaxStudents());
        course.setDifficulty(request.getDifficulty());
        course.setDescription(com.microcourse.util.XssSanitizer.sanitize(request.getDescription()));
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
        // P0#4 修复:已关闭(CLOSED)或已归档(ARCHIVED)课程禁止编辑
        if (course.getStatus() != null
                && (course.getStatus() == CourseStatus.CLOSED.getCode()
                    || course.getStatus() == CourseStatus.ARCHIVED.getCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "课程已关闭或归档，无法编辑");
        }
        // Published course cannot be edited directly
        if (course.getStatus() != null && course.getStatus() == CourseStatus.PUBLISHED.getCode()) {
            throw new BusinessException(ErrorCode.COURSE_PUBLISHED_CANNOT_EDIT);
        }

        // Partial update
        // P1 安全修复: XSS 净化更新字段
        if (request.getTitle() != null) course.setTitle(com.microcourse.util.XssSanitizer.sanitizePlainText(request.getTitle()));
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
        if (request.getSubtitle() != null) course.setSubtitle(com.microcourse.util.XssSanitizer.sanitizePlainText(request.getSubtitle()));
        if (request.getSummary() != null) course.setSummary(com.microcourse.util.XssSanitizer.sanitize(request.getSummary()));
        if (request.getCoverUrl() != null) course.setCoverUrl(request.getCoverUrl());
        if (request.getOfferDepartmentId() != null) course.setOfferDepartmentId(request.getOfferDepartmentId());
        if (request.getSemester() != null) course.setSemester(request.getSemester());
        if (request.getCreditHours() != null) course.setCreditHours(request.getCreditHours());
        if (request.getCourseNature() != null) course.setCourseNature(request.getCourseNature());
        if (request.getMaxStudents() != null) course.setMaxStudents(request.getMaxStudents());
        if (request.getDifficulty() != null) course.setDifficulty(request.getDifficulty());
        if (request.getDescription() != null) course.setDescription(com.microcourse.util.XssSanitizer.sanitize(request.getDescription()));
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
        // ★ Round 9-2 修复：课程更新后清除缓存，保证一致性（硬约束 #1）
        evictCourseCacheAfterCommit(id);
        return convertToVO(course, null, null, reviewRepository.countByCourseId(course.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        Course course = courseRepository.selectById(id);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        // P0: 所有权校验，防止越权修改他人课程状态
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
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

        if (status == CourseStatus.PUBLISHED.getCode()) {
            course.setPublishedAt(LocalDateTime.now());
        }

        // SECURITY: 乐观锁 CAS 更新（@Version 自动管理）
        boolean updated = courseRepository.updateById(course) > 0;
        if (!updated) {
            throw new BusinessException(ErrorCode.COURSE_STATUS_TRANSITION_NOT_ALLOWED, "课程状态已被其他操作修改，请刷新后重试");
        }
        recordReviewLog(id, "UPDATE", currentStatus, status, null);
        // ★ Round 9-2 修复：状态变更后清除缓存，保证一致性（硬约束 #1）
        evictCourseCacheAfterCommit(id);
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
        // P0#2 修复:提交审核前置校验——课程标题不能为空
        if (course.getTitle() == null || course.getTitle().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "课程标题不能为空");
        }
        // 提交审核前置校验——课程分类不能为空（DEVIATION-3 修复）
        if (course.getCategoryId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "课程必须选择分类");
        }
        // P0#2 修复:提交审核前置校验——课程必须包含至少一个章节
        LambdaQueryWrapper<CourseChapter> chapterCountWrapper = new LambdaQueryWrapper<>();
        chapterCountWrapper.eq(CourseChapter::getCourseId, id);
        if (chapterRepository.selectCount(chapterCountWrapper) <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "课程必须包含至少一个章节");
        }
        // P0-A7 修复:提交审核前置校验——课程必须有封面
        if (course.getCoverUrl() == null || course.getCoverUrl().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "课程必须设置封面");
        }
        // P0-A7 修复:提交审核前置校验——课程必须有简介（summary 或 description）
        boolean hasSummary = course.getSummary() != null && !course.getSummary().isBlank();
        boolean hasDescription = course.getDescription() != null && !course.getDescription().isBlank();
        if (!hasSummary && !hasDescription) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "课程必须填写简介或描述");
        }
        // P0-A7 修复:提交审核前置校验——课程必须包含至少一个视频或互动课件（PPT）
        LambdaQueryWrapper<Video> videoCountWrapper = new LambdaQueryWrapper<>();
        videoCountWrapper.eq(Video::getCourseId, id)
                .eq(Video::getStatus, VideoStatus.COMPLETED.getCode());
        long videoCount = videoRepository.selectCount(videoCountWrapper);
        LambdaQueryWrapper<CourseSlide> slideCountWrapper = new LambdaQueryWrapper<>();
        slideCountWrapper.eq(CourseSlide::getCourseId, id);
        long slideCount = courseSlideMapper.selectCount(slideCountWrapper);
        if (videoCount <= 0 && slideCount <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "课程必须包含至少一个视频或互动课件（PPT）");
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
        // ★ Round 9-2 修复：提交审核后清除缓存，保证一致性（硬约束 #1）
        evictCourseCacheAfterCommit(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        // R1-API-004 修复:保留 COURSE_NOT_FOUND 错误码区分
        Course course = courseRepository.selectById(id);
        if (course == null) {
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
        // ★ Round 9-2 修复：审核通过后清除缓存，保证一致性（硬约束 #1）
        evictCourseCacheAfterCommit(id);
        // Phase B-2 (P0-7)：审核通过后异步通知课程教师，@Async 不阻塞审批主流程
        if (course.getTeacherId() != null) {
            notificationService.notifyAsync(
                    course.getTeacherId(),
                    NotificationType.COURSE_APPROVED,
                    "课程已通过审核",
                    "您的课程《" + course.getTitle() + "》已通过审核，可以发布了",
                    id);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String reason) {
        // R1-API-004 修复:保留 COURSE_NOT_FOUND 错误码区分
        Course course = courseRepository.selectById(id);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        if (!SecurityUtil.isAdminOrAcademic()) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        // P0#4 修复:驳回原因存储前 XSS 净化,防止存储型 XSS
        String safeReason = XssSanitizer.sanitizePlainText(reason);
        // PENDING_REVIEW(1) → REJECTED(3) CAS
        int affected = courseRepository.update(null,
                new LambdaUpdateWrapper<Course>()
                        .eq(Course::getId, id)
                        .eq(Course::getStatus, CourseStatus.PENDING_REVIEW.getCode())
                        .set(Course::getStatus, CourseStatus.REJECTED.getCode())
                        .set(Course::getRejectReason, safeReason)
                        .set(Course::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = COALESCE(version, 0) + 1"));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.COURSE_STATUS_TRANSITION_NOT_ALLOWED);
        }
        recordReviewLog(id, "REJECT", CourseStatus.PENDING_REVIEW.getCode(),
                CourseStatus.REJECTED.getCode(), safeReason);
        // ★ Round 9-2 修复：审核驳回后清除缓存，保证一致性（硬约束 #1）
        evictCourseCacheAfterCommit(id);
        // Phase B-2 (P0-7)：审核驳回后异步通知课程教师，@Async 不阻塞审批主流程
        if (course.getTeacherId() != null) {
            notificationService.notifyAsync(
                    course.getTeacherId(),
                    NotificationType.COURSE_REJECTED,
                    "课程被驳回",
                    "您的课程《" + course.getTitle() + "》被驳回，原因：" + (safeReason != null ? safeReason : "未填写"),
                    id);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        // R1-API-004 修复:保留 COURSE_NOT_FOUND 错误码区分
        Course course = courseRepository.selectById(id);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        // ADMIN/ACADEMIC 可发布任意课程；TEACHER 只能发布自己的课程
        if (!SecurityUtil.isAdminOrAcademic() && !SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
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
        // ★ Round 9-2 修复：课程上架后清除缓存，保证一致性（硬约束 #1）
        evictCourseCacheAfterCommit(id);
        // C2-3 修复：课程上架后异步通知所有已选（未取消）学生。
        // 原实现同步执行分页循环发通知，阻塞 publish 事务。现改为 @Async 线程池异步执行，
        // publish 主事务立即提交返回，通知发送在后台线程中独立完成。
        final String courseTitle = course.getTitle();
        taskExecutor.execute(() -> notifyEnrolledStudentsOnPublish(id, courseTitle));
    }

    /** Round 9-1：发布通知分批批大小，避免一次性加载海量选课导致 OOM。 */
    private static final int PUBLISH_NOTIFY_BATCH_SIZE = 500;

    /**
     * Round 9-1（OOM 防护）：分批遍历课程「未取消」选课学生并异步发送上架通知。
     * 按主键 id 升序稳定分页，每批仅查询 user_id 字段，遍历过程中内存占用恒定，不随选课规模增长；
     * 每个学生恰好被通知一次（语义与原全量遍历一致）。@TableLogic 自动追加 deleted_at IS NULL，
     * 与原 selectList 的软删过滤完全一致。
     */
    private void notifyEnrolledStudentsOnPublish(Long courseId, String courseTitle) {
        long pageNo = 1;
        while (true) {
            Page<Enrollment> page = new Page<>(pageNo, PUBLISH_NOTIFY_BATCH_SIZE);
            page.setSearchCount(false); // 不执行 count 查询，进一步降低开销
            IPage<Enrollment> result = enrollmentRepository.selectPage(page,
                    new LambdaQueryWrapper<Enrollment>()
                            .eq(Enrollment::getCourseId, courseId)
                            .ne(Enrollment::getEnrollmentStatus, EnrollmentStatus.CANCELLED.getValue())
                            .select(Enrollment::getId, Enrollment::getUserId)
                            .orderByAsc(Enrollment::getId));
            List<Enrollment> batch = result.getRecords();
            if (batch.isEmpty()) {
                break;
            }
            for (Enrollment en : batch) {
                if (en.getUserId() != null) {
                    notificationService.notifyAsync(
                            en.getUserId(),
                            NotificationType.COURSE_PUBLISHED,
                            "课程已上架",
                            "您关注的课程《" + courseTitle + "》已上架，快去学习吧！",
                            courseId);
                }
            }
            if (batch.size() < PUBLISH_NOTIFY_BATCH_SIZE) {
                break;
            }
            pageNo++;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Course course = courseRepository.selectById(id);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }

        // TEACHER 只能删除自己的课程
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        // FK 检查：有活跃选课记录时禁止关闭 (DF-008 修复: 排除 CANCELLED/WAITLIST)
        long enrollCount = enrollmentRepository.selectCount(
                new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getCourseId, id)
                        .notIn(Enrollment::getEnrollmentStatus, "CANCELLED", "WAITLIST"));
        if (enrollCount > 0) {
            throw new BusinessException(ErrorCode.COURSE_HAS_ENROLLMENTS);
        }

        Integer currentStatus = course.getStatus() != null ? course.getStatus() : CourseStatus.DRAFT.getCode();
        CourseStatus fromStatus = CourseStatus.fromCode(currentStatus);

        // ★ 业务逻辑审计 P2-1 修复：ARCHIVED 终态保护（删课前必检）
        if (fromStatus == CourseStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.COURSE_STATUS_TRANSITION_NOT_ALLOWED, "已归档课程不可操作");
        }

        // DRAFT / REJECTED → CLOSED CAS
        if (currentStatus == CourseStatus.DRAFT.getCode()
                || currentStatus == CourseStatus.REJECTED.getCode()) {
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
        } else if (currentStatus == CourseStatus.CLOSED.getCode()) {
            // 已下架, 直接进入下方统一软删除
        } else {
            // PUBLISHED → CLOSED 等合法转换
            self.updateStatus(id, CourseStatus.CLOSED.getCode());
        }

        // 统一软删除: 使用 LambdaUpdateWrapper 直接设置 deleted_at
        // (updateById 可能被 @TableLogic 拦截导致 deletedAt 未写入 DB)
        courseRepository.update(null,
                new LambdaUpdateWrapper<Course>()
                        .eq(Course::getId, id)
                        .set(Course::getDeletedAt, LocalDateTime.now())
                        .set(Course::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));

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
        // ★ Round 9-2 修复：课程删除后清除缓存，保证一致性（硬约束 #1）
        evictCourseCacheAfterCommit(id);
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

        // 查询源课程的章节结构
        LambdaQueryWrapper<CourseChapter> chapterWrapper = new LambdaQueryWrapper<>();
        chapterWrapper.eq(CourseChapter::getCourseId, id)
                .orderByAsc(CourseChapter::getSortOrder);
        List<CourseChapter> sourceChapters = chapterRepository.selectList(chapterWrapper);

        // 复制章节结构（不含视频文件，只复制章节元数据）
        // C2-4: 先收集再循环 insert，所有 insert 在 @Transactional 事务内共享同一连接。
        List<CourseChapter> newChapters = new ArrayList<>();
        for (CourseChapter srcChapter : sourceChapters) {
            CourseChapter newChapter = new CourseChapter();
            newChapter.setCourseId(newCourse.getId());
            newChapter.setTitle(srcChapter.getTitle());
            newChapter.setDescription(srcChapter.getDescription());
            newChapter.setSortOrder(srcChapter.getSortOrder());
            newChapter.setChapterType(srcChapter.getChapterType());
            newChapter.setDuration(0);
            newChapter.setCreatedAt(LocalDateTime.now());
            newChapter.setUpdatedAt(LocalDateTime.now());
            newChapter.setVersion(0);
            newChapters.add(newChapter);
        }
        if (!newChapters.isEmpty()) {
            // C2-4: 循环插入在 @Transactional 事务内，所有 insert 共享同一 DB 连接和事务。
            // 对于典型课程（< 50 章节），逐条 insert 开销可接受；future 可改为 MyBatis BATCH executor。
            for (CourseChapter ch : newChapters) {
                chapterRepository.insert(ch);
            }
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
        // ★ 业务逻辑审计 P2-1 修复：使用枚举 canTransitionTo 集中白名单
        CourseStatus fromStatus = CourseStatus.fromCode(from);
        CourseStatus toStatus = CourseStatus.fromCode(to);
        if (fromStatus == null || toStatus == null) {
            return false;
        }
        return fromStatus.canTransitionTo(toStatus);
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