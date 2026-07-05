package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.ChapterVO;
import com.microcourse.dto.CoursePageQuery;
import com.microcourse.dto.CourseVO;
import com.microcourse.dto.PageResult;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseCategory;
import com.microcourse.entity.CourseChapter;
import com.microcourse.entity.User;
import com.microcourse.enums.CourseStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.CourseCategoryRepository;
import com.microcourse.repository.CourseReviewRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.CourseQueryService;
import com.microcourse.util.CourseCacheConstants;
import com.microcourse.util.RedisUtil;
import com.microcourse.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class CourseQueryServiceImpl implements CourseQueryService {

    private static final Logger LOG = LoggerFactory.getLogger(CourseQueryServiceImpl.class);

    // ★ Round 9-2 修复：课程详情缓存（5 分钟）—— 常量已迁移至 CourseCacheConstants
    // ★ Round 9-2 修复：课程统计缓存（1 小时）—— 常量已迁移至 CourseCacheConstants

    private final CourseRepository courseRepository;
    private final CourseCategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final CourseChapterRepository chapterRepository;
    private final CourseReviewRepository reviewRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final RedisUtil redisUtil;

    public CourseQueryServiceImpl(CourseRepository courseRepository,
                                  CourseCategoryRepository categoryRepository,
                                  UserRepository userRepository,
                                  CourseChapterRepository chapterRepository,
                                  CourseReviewRepository reviewRepository,
                                  EnrollmentRepository enrollmentRepository,
                                  RedisUtil redisUtil) {
        this.courseRepository = courseRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.chapterRepository = chapterRepository;
        this.reviewRepository = reviewRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.redisUtil = redisUtil;
    }

    @Override
    public PageResult<CourseVO> page(CoursePageQuery query) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        // ★ Round 8-4 修复(P0)：TEACHER 课程列表隔离 —— 教师只能看到自己创建的课程，
        // 不可越权浏览全平台课程；ADMIN / ACADEMIC 不受限（看到全部）。
        // STUDENT 默认只返回 APPROVED(2) + PUBLISHED(4)，见下方 status 过滤逻辑。
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
        // P2 修复: 按教师姓名搜索（join users 表）
        if (query.getTeacherName() != null && !query.getTeacherName().isBlank()) {
            LambdaQueryWrapper<User> teacherNameWrapper = new LambdaQueryWrapper<>();
            teacherNameWrapper.select(User::getId).like(User::getRealName, query.getTeacherName());
            List<Long> teacherIds = userRepository.selectList(teacherNameWrapper)
                    .stream().map(User::getId).collect(Collectors.toList());
            if (teacherIds.isEmpty()) {
                wrapper.eq(Course::getId, -1L);
            } else {
                wrapper.in(Course::getTeacherId, teacherIds);
            }
        }
        if (query.getStatus() != null) {
            wrapper.eq(Course::getStatus, query.getStatus());
        } else if (!teacherScoped) {
            if (SecurityUtil.hasRole("STUDENT")) {
                // STUDENT只能看到APPROVED(2)和PUBLISHED(4)的课程
                wrapper.in(Course::getStatus, List.of(
                    CourseStatus.APPROVED.getCode(), CourseStatus.PUBLISHED.getCode()));
            } else {
                // 非STUDENT角色默认排除已删除（CLOSED）和已归档（ARCHIVED）
                wrapper.notIn(Course::getStatus, List.of(
                    CourseStatus.CLOSED.getCode(), CourseStatus.ARCHIVED.getCode()));
            }
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
        // P1C-004: 按开课院系筛选（offer_department_id）
        if (query.getOfferDepartmentId() != null) {
            wrapper.eq(Course::getOfferDepartmentId, query.getOfferDepartmentId());
        }
        // 排序
        String sortBy = query.getSortBy();
        String sortOrder = "asc".equalsIgnoreCase(query.getSortOrder()) ? "asc" : "desc";
        if (sortBy != null && !CoursePageQuery.VALID_SORT_BY.contains(sortBy)) {
            sortBy = null;
        }
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
        Map<Long, CourseCategory> categoryMap = buildCategoryMap(records);
        Map<Long, User> teacherMap = buildTeacherMap(records);
        Map<Long, Long> ratingCountMap = buildRatingCountMap(records);

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

    @Override
    public CourseVO getById(Long id) {
        // ★ Round 9-2 修复：1) 查缓存（Redis 故障降级回 DB，硬约束 #2）
        String cacheKey = CourseCacheConstants.COURSE_CACHE_PREFIX + id;
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
            redisUtil.set(cacheKey, vo, CourseCacheConstants.COURSE_CACHE_TTL, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOG.warn("[Round9-2] 课程详情缓存写入失败, id={}", id, e);
        }

        // TEACHER 隔离：教师只能查看自己的课程（P1-C）
        if (SecurityUtil.hasRole("TEACHER") && !SecurityUtil.isAdmin() &&
            !vo.getTeacherId().equals(SecurityUtil.getCurrentUserId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        // 数据隔离：CLOSED/ARCHIVED 课程对学生等非授权角色不可见（移自 Controller getById）
        Integer statusVal = vo.getStatus();
        if (statusVal == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "课程状态不能为空");
        }
        int st = statusVal.intValue();
        if (st == CourseStatus.CLOSED.getCode() || st == CourseStatus.ARCHIVED.getCode()) {
            boolean privileged = SecurityUtil.isAdmin() || SecurityUtil.hasRole("ACADEMIC");
            boolean ownerTeacher = SecurityUtil.hasRole("TEACHER")
                    && vo.getTeacherId() != null
                    && vo.getTeacherId().equals(SecurityUtil.getCurrentUserId());
            if (!privileged && !ownerTeacher) {
                throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
            }
        }
        // REJECTED/DRAFT 课程对 STUDENT 角色也不可见
        if (SecurityUtil.hasRole("STUDENT")) {
            if (st == CourseStatus.REJECTED.getCode() || st == CourseStatus.DRAFT.getCode()) {
                throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
            }
        }

        return vo;
    }

    /**
     * N+1 修复（提取自 page()）：从课程记录批量提取 categoryId 并一次性查询，
     * 返回 categoryId -> CourseCategory 的预加载 Map。空集合时返回空 Map。
     */
    private Map<Long, CourseCategory> buildCategoryMap(List<Course> records) {
        Map<Long, CourseCategory> categoryMap = new java.util.HashMap<>();
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
     * 返回 teacherId -> User 的预加载 Map。空集合时返回空 Map。
     */
    private Map<Long, User> buildTeacherMap(List<Course> records) {
        Map<Long, User> teacherMap = new java.util.HashMap<>();
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
     * 返回 courseId -> 评价数 的预加载 Map。空记录时返回空 Map。
     */
    private Map<Long, Long> buildRatingCountMap(List<Course> records) {
        Map<Long, Long> ratingCountMap = new java.util.HashMap<>();
        if (!records.isEmpty()) {
            List<Long> courseIds = records.stream()
                    .map(Course::getId).collect(Collectors.toList());
            List<Map<String, Object>> countRows = reviewRepository.countByCourseIds(courseIds);
            for (Map<String, Object> row : countRows) {
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

    /**
     * 从预加载的 Map 中装配 CourseVO（仅用于 page() 分页场景），
     * 避免每行独立查询 category/teacher 造成的 N+1。
     */
    private CourseVO convertToVOFromMaps(Course course, Map<Long, CourseCategory> categoryMap,
                                         Map<Long, User> teacherMap,
                                         Map<Long, Long> ratingCountMap) {
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
        vo.setListPrice(course.getListPrice());
        vo.setFreeAccessScope(course.getFreeAccessScope());
        vo.setFreeAccessScopeLabel(getFreeAccessScopeLabel(course.getFreeAccessScope()));
        vo.setDiscountScope(course.getDiscountScope());
        vo.setDiscountPercent(course.getDiscountPercent());
        vo.setPricingStatus(course.getPricingStatus());

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

    /**
     * 装配单个 CourseVO（用于 getById() 详情场景），接受预加载的 category/teacher。
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
        vo.setListPrice(course.getListPrice());
        vo.setFreeAccessScope(course.getFreeAccessScope());
        vo.setFreeAccessScopeLabel(getFreeAccessScopeLabel(course.getFreeAccessScope()));
        vo.setDiscountScope(course.getDiscountScope());
        vo.setDiscountPercent(course.getDiscountPercent());
        vo.setPricingStatus(course.getPricingStatus());

        if (course.getStatus() != null) {
            vo.setStatusText(CourseStatus.getDescription(course.getStatus()));
        }

        // Category name — 优先用预加载对象, fallback selectById
        if (course.getCategoryId() != null) {
            CourseCategory category = preloadedCategory != null ? preloadedCategory
                    : categoryRepository.selectById(course.getCategoryId());
            if (category != null) {
                vo.setCategoryName(category.getName());
            }
        }

        // Teacher name — 优先用预加载对象, fallback selectById
        if (course.getTeacherId() != null) {
            User teacher = preloadedTeacher != null ? preloadedTeacher
                    : userRepository.selectById(course.getTeacherId());
            if (teacher != null) {
                vo.setTeacherName(teacher.getRealName());
            }
        }

        return vo;
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

    private String getFreeAccessScopeLabel(String scope) {
        if (scope == null) return null;
        switch (scope) {
            case "same_department": return "同院系免费";
            case "same_college":    return "同学院免费";
            case "same_school":     return "同校免费";
            default:                return null;
        }
    }
}
