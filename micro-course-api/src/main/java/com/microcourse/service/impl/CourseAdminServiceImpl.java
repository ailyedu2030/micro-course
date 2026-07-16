package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.microcourse.dto.CourseCreateRequest;
import com.microcourse.dto.CourseUpdateRequest;
import com.microcourse.dto.CourseVO;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseCategory;
import com.microcourse.entity.CourseNote;
import com.microcourse.entity.CourseChapter;
import com.microcourse.entity.User;
import com.microcourse.entity.Video;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.LearningProgress;
import com.microcourse.entity.DiscussionComment;
import com.microcourse.entity.DiscussionPost;
import com.microcourse.entity.VideoBookmark;
import com.microcourse.entity.Exercise;
import com.microcourse.enums.CourseStatus;
import com.microcourse.enums.EnrollmentStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.entity.CourseSlide;
import com.microcourse.plugin.interactive.entity.SlidePage;
import com.microcourse.plugin.interactive.mapper.CourseSlideMapper;
import com.microcourse.plugin.interactive.mapper.SlidePageMapper;
import com.microcourse.repository.CourseCategoryRepository;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.LearningProgressRepository;
import com.microcourse.repository.VideoRepository;
import com.microcourse.repository.CourseReviewRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.DiscussionPostRepository;
import com.microcourse.repository.CourseNoteRepository;
import com.microcourse.repository.DiscussionCommentRepository;
import com.microcourse.repository.ExerciseRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.repository.VideoBookmarkRepository;
import com.microcourse.repository.PluginGrantRepository;
import com.microcourse.dto.BatchOperationResult;
import com.microcourse.service.CourseAdminService;
import com.microcourse.service.CourseAuditService;
import com.microcourse.service.CourseStateMachine;
import com.microcourse.service.CourseStateMachine.TransitionContext;
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
import java.util.stream.Collectors;

@Service
public class CourseAdminServiceImpl implements CourseAdminService {

    private static final Logger LOG = LoggerFactory.getLogger(CourseAdminServiceImpl.class);

    private final CourseRepository courseRepository;
    private final CourseCategoryRepository categoryRepository;
    private final CourseChapterRepository chapterRepository;
    private final UserRepository userRepository;
    private final CourseReviewRepository reviewRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final PluginGrantRepository pluginGrantRepository;
    private final VideoRepository videoRepository;
    private final LearningProgressRepository learningProgressRepository;
    private final DiscussionPostRepository discussionPostRepository;
    private final DiscussionCommentRepository discussionCommentRepository;
    private final CourseNoteRepository courseNoteRepository;
    private final VideoBookmarkRepository videoBookmarkRepository;
    private final ExerciseRepository exerciseRepository;
    private final CourseSlideMapper courseSlideMapper;
    private final SlidePageMapper slidePageMapper;
    private final CourseAuditService auditService;
    private final CourseStateMachine courseStateMachine;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Value("${upload.base-dir:uploads}")
    private String uploadBaseDir;

    public CourseAdminServiceImpl(CourseRepository courseRepository,
                                  CourseCategoryRepository categoryRepository,
                                  CourseChapterRepository chapterRepository,
                                  VideoRepository videoRepository,
                                  UserRepository userRepository,
                                  CourseReviewRepository reviewRepository,
                                  EnrollmentRepository enrollmentRepository,
                                  PluginGrantRepository pluginGrantRepository,
                                  LearningProgressRepository learningProgressRepository,
                                  DiscussionCommentRepository discussionCommentRepository,
                                  CourseNoteRepository courseNoteRepository,
                                  VideoBookmarkRepository videoBookmarkRepository,
                                  ExerciseRepository exerciseRepository,
                                  DiscussionPostRepository discussionPostRepository,
                                  CourseSlideMapper courseSlideMapper,
                                  SlidePageMapper slidePageMapper,
                                  CourseAuditService auditService,
                                  CourseStateMachine courseStateMachine,
                                  com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.courseRepository = courseRepository;
        this.categoryRepository = categoryRepository;
        this.chapterRepository = chapterRepository;
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.pluginGrantRepository = pluginGrantRepository;
        this.videoRepository = videoRepository;
        this.learningProgressRepository = learningProgressRepository;
        this.discussionCommentRepository = discussionCommentRepository;
        this.courseNoteRepository = courseNoteRepository;
        this.videoBookmarkRepository = videoBookmarkRepository;
        this.exerciseRepository = exerciseRepository;
        this.discussionPostRepository = discussionPostRepository;
        this.courseSlideMapper = courseSlideMapper;
        this.slidePageMapper = slidePageMapper;
        this.auditService = auditService;
        this.courseStateMachine = courseStateMachine;
        this.objectMapper = objectMapper;
    }

    private void checkPluginGrant(Long teacherId, String courseType) {
        /* ---- 【C-1 修复】OFFLINE 不要求互动课件插件授权 ---- */
        /* 【根因】条件 `"VIDEO".equals(courseType)` 只排除 VIDEO，导致 OFFLINE 也被要求 interactive 插件授权 */
        /* 【修复】改为只对 INTERACTIVE 类型检查，其他类型自动跳过 */
        /* 【防止再发】条件翻转 `!"INTERACTIVE".equals` 确保未来新增类型也不会误触发 */
        if (courseType == null || !"INTERACTIVE".equals(courseType)) return;
        if (SecurityUtil.isAdmin()) return;
        LambdaQueryWrapper<com.microcourse.entity.PluginGrant> q = new LambdaQueryWrapper<>();
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
        if (request.getCategoryId() != null && categoryRepository.selectById(request.getCategoryId()) == null) {
            throw new BusinessException(ErrorCode.COURSE_CATEGORY_NOT_FOUND);
        }
        if (request.getTeacherId() != null && userRepository.selectById(request.getTeacherId()) == null) {
            throw new BusinessException(ErrorCode.COURSE_TEACHER_NOT_FOUND);
        }
        checkPluginGrant(request.getTeacherId(), request.getCourseType());

        if (SecurityUtil.hasRole("TEACHER") && !SecurityUtil.isAdmin()) {
            request.setTeacherId(SecurityUtil.getCurrentUserId());
        }

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
        course.setDescription(com.microcourse.util.XssSanitizer.sanitize(request.getDescription()));
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

        // P1 Stage 1: 课程级元信息(交叉审查 P2-1 抽取 helper,避免 create/update 重复)
        applyP1CourseMetaFromCreate(course, request);

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
        if (CourseStatus.PUBLISHED.getCode() == course.getStatus()) {
            throw new BusinessException(ErrorCode.COURSE_PUBLISHED_CANNOT_EDIT);
        }

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
        if (request.getDescription() != null) course.setDescription(com.microcourse.util.XssSanitizer.sanitize(request.getDescription()));
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

        // P1 Stage 1: 课程级元信息(交叉审查 P2-1 抽取 helper)
        applyP1CourseMetaFromUpdate(course, request);

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
            throw new BusinessException(ErrorCode.COURSE_ARCHIVED);
        }

        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        long enrollCount = enrollmentRepository.selectCount(
                new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getCourseId, id)
                        .notIn(Enrollment::getEnrollmentStatus, EnrollmentStatus.CANCELLED.getValue(),
                                EnrollmentStatus.WAITLIST.getValue()));
        if (enrollCount > 0) {
            throw new BusinessException(ErrorCode.COURSE_HAS_ENROLLMENTS);
        }

        if (course.getCoverUrl() != null && !course.getCoverUrl().isBlank()) {
            try {
                Path coverPath = Paths.get(uploadBaseDir, course.getCoverUrl());
                Files.deleteIfExists(coverPath);
                LOG.info("[P2-7] 封面文件已清理 path={}", coverPath);
            } catch (Exception e) {
                LOG.warn("[P2-7] 封面文件清理失败 url={}", course.getCoverUrl(), e);
            }
        }

        int affected = courseRepository.update(null,
                new LambdaUpdateWrapper<Course>()
                        .eq(Course::getId, id)
                        .eq(Course::getStatus, currentStatus)
                        .set(Course::getStatus, CourseStatus.CLOSED.getCode())
                        .set(Course::getDeletedAt, LocalDateTime.now())
                        .set(Course::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = COALESCE(version, 0) + 1"));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.COURSE_STATUS_TRANSITION_NOT_ALLOWED);
        }

        chapterRepository.update(null,
                new LambdaUpdateWrapper<CourseChapter>()
                        .eq(CourseChapter::getCourseId, id)
                        .set(CourseChapter::getDeletedAt, LocalDateTime.now()));
        videoRepository.update(null,
                new LambdaUpdateWrapper<Video>()
                        .eq(Video::getCourseId, id)
                        .set(Video::getDeletedAt, LocalDateTime.now()));

        learningProgressRepository.delete(new LambdaQueryWrapper<LearningProgress>()
                .eq(LearningProgress::getCourseId, id));

        exerciseRepository.update(null,
                new LambdaUpdateWrapper<Exercise>()
                        .eq(Exercise::getCourseId, id)
                        .set(Exercise::getDeletedAt, LocalDateTime.now()));

        courseSlideMapper.delete(new LambdaQueryWrapper<CourseSlide>()
                .eq(CourseSlide::getCourseId, id));
        slidePageMapper.delete(new LambdaQueryWrapper<SlidePage>()
                .eq(SlidePage::getCourseId, id));

        discussionPostRepository.update(null,
                new LambdaUpdateWrapper<DiscussionPost>()
                        .eq(DiscussionPost::getCourseId, id)
                        .set(DiscussionPost::getDeletedAt, LocalDateTime.now()));

        List<Long> postIds = discussionPostRepository.selectList(
                new LambdaQueryWrapper<DiscussionPost>()
                        .eq(DiscussionPost::getCourseId, id)
                        .select(DiscussionPost::getId))
                .stream().map(DiscussionPost::getId).collect(Collectors.toList());
        if (!postIds.isEmpty()) {
            discussionCommentRepository.update(null,
                    new LambdaUpdateWrapper<DiscussionComment>()
                            .in(DiscussionComment::getPostId, postIds)
                            .set(DiscussionComment::getDeletedAt, LocalDateTime.now()));
        }

        courseNoteRepository.delete(new LambdaQueryWrapper<CourseNote>()
                .eq(CourseNote::getCourseId, id));

        List<Long> videoIds = videoRepository.selectList(
                new LambdaQueryWrapper<Video>()
                        .eq(Video::getCourseId, id)
                        .select(Video::getId))
                .stream().map(Video::getId).collect(Collectors.toList());
        if (!videoIds.isEmpty()) {
            videoBookmarkRepository.delete(new LambdaQueryWrapper<VideoBookmark>()
                    .in(VideoBookmark::getVideoId, videoIds));
        }

        LOG.info("课程已关闭（含级联清理）, id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseVO copy(Long id) {
        Course original = getCourseOrThrow(id);
        if (!SecurityUtil.isOwnerOrAdmin(original.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

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

        LambdaQueryWrapper<CourseChapter> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseChapter::getCourseId, id).orderByAsc(CourseChapter::getSortOrder);
        List<CourseChapter> chapters = chapterRepository.selectList(wrapper);
        for (CourseChapter ch : chapters) {
            Long originalChapterId = ch.getId();
            CourseChapter copyCh = new CourseChapter();
            copyCh.setCourseId(course.getId());
            copyCh.setTitle(ch.getTitle());
            copyCh.setDescription(ch.getDescription());
            copyCh.setSortOrder(ch.getSortOrder());
            copyCh.setDuration(ch.getDuration());
            copyCh.setLearningObjectives(ch.getLearningObjectives());
            chapterRepository.insert(copyCh);

            // P1-C-03 修复: 复制章节下的视频（仅元数据，不复制实际视频文件）
            List<Video> videos = videoRepository.selectList(
                    new LambdaQueryWrapper<Video>()
                            .eq(Video::getChapterId, originalChapterId)
                            .isNull(Video::getDeletedAt));
            for (Video v : videos) {
                Video copyV = new Video();
                copyV.setChapterId(copyCh.getId());
                copyV.setCourseId(course.getId());
                copyV.setTitle(v.getTitle());
                copyV.setSortOrder(v.getSortOrder());
                copyV.setDuration(v.getDuration());
                videoRepository.insert(copyV);
            }
        }

        LOG.info("课程复制成功, originalId={}, newId={}, operator={}", id, course.getId());
        CourseVO vo = convertToVO(course);
        vo.setVideoCopied(true);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseVO updateCover(Long id, MultipartFile file) {
        Course course = getCourseOrThrow(id);
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        Course course = getCourseOrThrow(id);
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        CourseStatus target = CourseStatus.fromCode(status);
        if (target == null) {
            throw new BusinessException(ErrorCode.COURSE_INVALID_STATUS, "无效的状态码: " + status);
        }
        // 【状态机重构】所有转换/守卫/乐观锁下沉到 CourseStateMachine
        // ARCHIVED 是业务终态, ARCHIVED ≠ CLOSED (归档是有序完结, 关闭是强制终止)
        User actor = SecurityUtil.getCurrentUser();
        courseStateMachine.transition(id, target, actor, TransitionContext.empty());
        LOG.info("课程状态更新, id={}, target={}", id, target);
    }

    private Course getCourseOrThrow(Long id) {
        Course course = courseRepository.selectById(id);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        return course;
    }

    private String fullCoverUrl(String coverUrl) {
        if (coverUrl != null && !coverUrl.startsWith("http") && !coverUrl.startsWith("/api/files/")) {
            return "/api/files/" + coverUrl;
        }
        return coverUrl;
    }

    private CourseVO convertToVO(Course course) {
        CourseVO vo = new CourseVO();
        vo.setId(course.getId());
        vo.setTitle(course.getTitle());
        vo.setSubtitle(course.getSubtitle());
        vo.setSummary(course.getSummary());
        vo.setCoverUrl(fullCoverUrl(course.getCoverUrl()));
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

        // P1 Stage 1: 课程级元信息(交叉审查 P1-1:VO 必须包含新字段,否则 Trae 看不到自己传的字段)
        vo.setHid(course.getHid());
        vo.setTotalHours(course.getTotalHours());
        vo.setTotalWeeks(course.getTotalWeeks());
        vo.setLearningMode(course.getLearningMode());
        vo.setEvaluationScheme(course.getEvaluationScheme());
        if (course.getTeachingPhilosophy() != null && !course.getTeachingPhilosophy().isBlank()) {
            try {
                vo.setTeachingPhilosophy(objectMapper.readValue(course.getTeachingPhilosophy(), java.util.List.class));
            } catch (Exception e) {
                LOG.warn("[CourseVO] teachingPhilosophy 反序列化失败: {}", e.getMessage());
                vo.setTeachingPhilosophy(java.util.Collections.emptyList());
            }
        }

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

    private String getFreeAccessScopeLabel(String scope) {
        if (scope == null) return null;
        switch (scope) {
            case "same_department": return "同院系免费";
            case "same_college":    return "同学院免费";
            case "same_school":     return "同校免费";
            default:                return null;
        }
    }

    // ───── 委托给 CourseAuditServiceImpl ─────

    @Override
    public void submitForReview(Long id) {
        auditService.submitForReview(id);
    }

    @Override
    public void approve(Long id) {
        auditService.approve(id);
    }

    @Override
    public void reject(Long id, String reason) {
        auditService.reject(id, reason);
    }

    @Override
    public void rejectToDraft(Long id) {
        Course course = getCourseOrThrow(id);
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        CourseStatus current = CourseStatus.fromCode(course.getStatus());
        if (current != CourseStatus.REJECTED) {
            throw new BusinessException(ErrorCode.COURSE_INVALID_STATUS, "只有已驳回的课程才能退回草稿");
        }
        courseStateMachine.transition(id, CourseStatus.DRAFT, SecurityUtil.getCurrentUser(), TransitionContext.empty());
    }

    @Override
    public void publish(Long id) {
        auditService.publish(id);
    }

    @Override
    public void unpublish(Long id) {
        auditService.unpublish(id);
    }

    @Override
    public BatchOperationResult batchApprove(List<Long> ids) {
        return auditService.batchApprove(ids);
    }

    @Override
    public BatchOperationResult batchReject(List<Long> ids, String reason) {
        return auditService.batchReject(ids, reason);
    }

    /**
     * P1 Stage 1 helper: 把 DTO 的 P1 课程级元信息字段写入 Entity
     * 交叉审查 P2-1: 抽取以避免 create/update 重复
     */
    private void applyP1CourseMetaFromCreate(Course course, com.microcourse.dto.CourseCreateRequest request) {
        applyP1CourseMeta(course, request.getHid(), request.getTotalHours(), request.getTotalWeeks(),
            request.getLearningMode(), request.getEvaluationScheme(), request.getTeachingPhilosophy());
    }

    private void applyP1CourseMetaFromUpdate(Course course, com.microcourse.dto.CourseUpdateRequest request) {
        applyP1CourseMeta(course, request.getHid(), request.getTotalHours(), request.getTotalWeeks(),
            request.getLearningMode(), request.getEvaluationScheme(), request.getTeachingPhilosophy());
    }

    private void applyP1CourseMeta(Course course, String hid, Integer totalHours, Integer totalWeeks,
                                    String learningMode, String evaluationScheme,
                                    java.util.List<String> teachingPhilosophy) {
        if (hid != null) course.setHid(hid);
        if (totalHours != null) course.setTotalHours(totalHours);
        if (totalWeeks != null) course.setTotalWeeks(totalWeeks);
        if (learningMode != null) course.setLearningMode(learningMode);
        if (evaluationScheme != null) course.setEvaluationScheme(evaluationScheme);
        if (teachingPhilosophy != null && !teachingPhilosophy.isEmpty()) {
            try {
                course.setTeachingPhilosophy(objectMapper.writeValueAsString(teachingPhilosophy));
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "teachingPhilosophy 序列化失败: " + e.getMessage());
            }
        }
    }
}
