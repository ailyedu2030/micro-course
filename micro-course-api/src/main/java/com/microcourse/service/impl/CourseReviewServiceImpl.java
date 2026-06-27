package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.CourseReviewRequest;
import com.microcourse.dto.CourseReviewVO;
import com.microcourse.dto.PageResult;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseReview;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.LearningProgress;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.CourseReviewRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.LearningProgressRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.CourseReviewService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 课程评价服务实现
 *
 * @author Phase9-Development-Team
 * @since 2026-06-12
 */
@Service
public class CourseReviewServiceImpl implements CourseReviewService {

    private final CourseReviewRepository courseReviewRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final LearningProgressRepository learningProgressRepository;

    public CourseReviewServiceImpl(CourseReviewRepository courseReviewRepository,
                                   EnrollmentRepository enrollmentRepository,
                                   UserRepository userRepository,
                                   CourseRepository courseRepository,
                                   LearningProgressRepository learningProgressRepository) {
        this.courseReviewRepository = courseReviewRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.learningProgressRepository = learningProgressRepository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseReviewVO create(Long courseId, CourseReviewRequest request, Long userId) {
        // E4: 回复评价前置校验——父评价必须存在且属于同一课程
        Long parentId = request.getParentId();
        if (parentId != null) {
            CourseReview parent = courseReviewRepository.selectById(parentId);
            if (parent == null) {
                throw new BusinessException(ErrorCode.COURSE_REVIEW_NOT_FOUND, "父评价不存在");
            }
            if (!parent.getCourseId().equals(courseId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "回复必须属于同一课程");
            }
            // 回复不需要重复校验选课状态和评分
        } else {
            // 顶级评价：验证用户已选修该课程
            LambdaQueryWrapper<Enrollment> enrollWrapper = new LambdaQueryWrapper<>();
            enrollWrapper.eq(Enrollment::getUserId, userId)
                    .eq(Enrollment::getCourseId, courseId);
            Enrollment enrollment = enrollmentRepository.selectOne(enrollWrapper);
            if (enrollment == null) {
                throw new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND);
            }

            // J10-01: 校验用户已完课才能评价（completed=true 或 progress >= 80%）
            if (!Boolean.TRUE.equals(enrollment.getCompleted())) {
                // 检查学习进度是否 ≥ 80%
                LambdaQueryWrapper<LearningProgress> progressWrapper = new LambdaQueryWrapper<>();
                progressWrapper.eq(LearningProgress::getUserId, userId)
                        .eq(LearningProgress::getCourseId, courseId);
                LearningProgress progress = learningProgressRepository.selectOne(progressWrapper);
                if (progress == null || progress.getVideoProgress() == null || progress.getVideoProgress() < 80) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "请完成课程学习后再评价（学习进度 ≥ 80%）");
                }
            }

            // 验证评分范围 1-5
            if (request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
                throw new BusinessException(ErrorCode.COURSE_REVIEW_INVALID_RATING);
            }
        }

        // 检查是否已评价（每人每课程只能评价一次，回复不受此限制）
        if (parentId == null) {
            LambdaQueryWrapper<CourseReview> reviewWrapper = new LambdaQueryWrapper<>();
            reviewWrapper.eq(CourseReview::getUserId, userId)
                    .eq(CourseReview::getCourseId, courseId)
                    .isNull(CourseReview::getParentId);
            CourseReview existing = courseReviewRepository.selectOne(reviewWrapper);
            if (existing != null) {
                throw new BusinessException(ErrorCode.COURSE_REVIEW_ALREADY_EXISTS);
            }
        }

        CourseReview review = new CourseReview();
        review.setCourseId(courseId);
        review.setUserId(userId);
        review.setRating(request.getRating() != null ? request.getRating() : 0);
        // P1 安全修复: XSS 净化评价内容
        review.setContent(com.microcourse.util.XssSanitizer.sanitize(request.getContent()));
        review.setIsAnonymous(request.getIsAnonymous() != null ? request.getIsAnonymous() : false);
        review.setParentId(parentId);
        review.setCreatedAt(LocalDateTime.now());
        review.setUpdatedAt(LocalDateTime.now());
        // 新评价默认通过状态（原有行为：无审核流程）
        review.setStatus(1);

        try {
            courseReviewRepository.insert(review);
        } catch (org.springframework.dao.DuplicateKeyException dupEx) {
            // CON-NEW-7 修复:DB uk_course_reviews_user_course 兜底,降级为业务异常
            throw new BusinessException(ErrorCode.COURSE_REVIEW_ALREADY_EXISTS);
        }
        // 只有顶级评价才更新课程平均评分
        if (parentId == null) {
            updateCourseAvgRating(courseId);
        }
        return convertToVO(review);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CourseReviewVO> listByCourse(Long courseId, int page, int size) {
        Page<CourseReview> pg = new Page<>(page + 1, size);
        LambdaQueryWrapper<CourseReview> wrapper = new LambdaQueryWrapper<>();
        // E4: 只查顶级评价（parent_id IS NULL），回复通过 replies 嵌套
        wrapper.eq(CourseReview::getCourseId, courseId)
                .isNull(CourseReview::getParentId)
                .orderByDesc(CourseReview::getCreatedAt);
        IPage<CourseReview> result = courseReviewRepository.selectPage(pg, wrapper);
        java.util.Map<Long, User> userMap = buildUserMap(result.getRecords());
        java.util.Map<Long, String> courseTitleMap = buildCourseTitleMap(result.getRecords());
        List<CourseReviewVO> vos = result.getRecords().stream()
                .map(r -> {
                    CourseReviewVO vo = convertToVO(r, userMap, courseTitleMap);
                    // E4: 加载回复（最多 20 条，按时间正序）
                    vo.setReplies(loadReplies(r.getId(), userMap, courseTitleMap));
                    return vo;
                })
                .collect(Collectors.toList());
        return PageResult.of(vos, result.getTotal(), page, size);
    }

    /**
     * E4: 加载某条评价的回复列表（按时间正序，最多 20 条）
     */
    private List<CourseReviewVO> loadReplies(Long parentId, java.util.Map<Long, User> userMap, java.util.Map<Long, String> courseTitleMap) {
        LambdaQueryWrapper<CourseReview> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseReview::getParentId, parentId)
                .orderByAsc(CourseReview::getCreatedAt);
        Page<CourseReview> pg = new Page<>(0, 20);
        pg.setSearchCount(false);
        IPage<CourseReview> pageResult = courseReviewRepository.selectPage(pg, wrapper);
        List<CourseReview> replies = pageResult.getRecords();
        if (replies.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        // 回复列表中追加新的用户信息
        java.util.Map<Long, User> replyUserMap = new java.util.HashMap<>(userMap);
        buildUserMap(replies).forEach((k, v) -> replyUserMap.putIfAbsent(k, v));
        return replies.stream()
                .map(r -> convertToVO(r, replyUserMap, courseTitleMap))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseReviewVO> listReplies(Long parentId) {
        LambdaQueryWrapper<CourseReview> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseReview::getParentId, parentId)
                .orderByAsc(CourseReview::getCreatedAt);
        List<CourseReview> replies = courseReviewRepository.selectList(wrapper);
        java.util.Map<Long, User> userMap = buildUserMap(replies);
        java.util.Map<Long, String> courseTitleMap = buildCourseTitleMap(replies);
        return replies.stream()
                .map(r -> convertToVO(r, userMap, courseTitleMap))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CourseReviewVO> getMyReviews(Long userId, int page, int size) {
        Page<CourseReview> pg = new Page<>(page + 1, size);
        LambdaQueryWrapper<CourseReview> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseReview::getUserId, userId)
                .orderByDesc(CourseReview::getCreatedAt);
        IPage<CourseReview> result = courseReviewRepository.selectPage(pg, wrapper);
        java.util.Map<Long, User> userMap = buildUserMap(result.getRecords());
        java.util.Map<Long, String> courseTitleMap = buildCourseTitleMap(result.getRecords());
        return PageResult.of(result.getRecords().stream()
                .map(r -> convertToVO(r, userMap, courseTitleMap))
                .collect(Collectors.toList()), result.getTotal(), page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CourseReviewVO> listAll(int page, int size, Long courseId) {
        Page<CourseReview> pg = new Page<>(page + 1, size);
        LambdaQueryWrapper<CourseReview> wrapper = new LambdaQueryWrapper<>();
        if (courseId != null) {
            wrapper.eq(CourseReview::getCourseId, courseId);
        }
        wrapper.orderByDesc(CourseReview::getCreatedAt);
        IPage<CourseReview> result = courseReviewRepository.selectPage(pg, wrapper);
        java.util.Map<Long, User> userMap = buildUserMap(result.getRecords());
        java.util.Map<Long, String> courseTitleMap = buildCourseTitleMap(result.getRecords());
        return PageResult.of(result.getRecords().stream()
                .map(r -> convertToVO(r, userMap, courseTitleMap))
                .collect(Collectors.toList()), result.getTotal(), page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteReview(Long id) {
        CourseReview review = courseReviewRepository.selectById(id);
        if (review == null) {
            throw new BusinessException(ErrorCode.COURSE_REVIEW_NOT_FOUND);
        }
        Long courseId = review.getCourseId();
        courseReviewRepository.deleteById(id);
        updateCourseAvgRating(courseId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveReview(Long id) {
        CourseReview review = courseReviewRepository.selectById(id);
        if (review == null) {
            throw new BusinessException(ErrorCode.COURSE_REVIEW_NOT_FOUND);
        }
        review.setStatus(1); // APPROVED
        review.setUpdatedAt(LocalDateTime.now());
        courseReviewRepository.updateById(review);
        updateCourseAvgRating(review.getCourseId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectReview(Long id) {
        CourseReview review = courseReviewRepository.selectById(id);
        if (review == null) {
            throw new BusinessException(ErrorCode.COURSE_REVIEW_NOT_FOUND);
        }
        review.setStatus(2); // REJECTED（逻辑驳回，不物理删除）
        review.setUpdatedAt(LocalDateTime.now());
        courseReviewRepository.updateById(review);
        updateCourseAvgRating(review.getCourseId());
    }

    private void updateCourseAvgRating(Long courseId) {
        // 原子 SQL 更新:避免 read-compute-write 并发丢失更新(CON-NEW 修复)
        // Round 11-4 安全修复:原 setSql 字符串拼接 courseId 存在 SQL 注入隐患,
        // 改用 CourseRepository.updateAvgRating 的 #{courseId} 参数化预编译占位符。
        courseRepository.updateAvgRating(courseId);
    }

    private CourseReviewVO convertToVO(CourseReview review) {
        return convertToVO(review, null);
    }

    private CourseReviewVO convertToVO(CourseReview review, java.util.Map<Long, User> userMap) {
        return convertToVO(review, userMap, null);
    }

    private CourseReviewVO convertToVO(CourseReview review, java.util.Map<Long, User> userMap, java.util.Map<Long, String> courseTitleMap) {
        CourseReviewVO vo = new CourseReviewVO();
        vo.setId(review.getId());
        vo.setCourseId(review.getCourseId());
        vo.setUserId(review.getUserId());
        vo.setRating(review.getRating());
        vo.setContent(review.getContent());
        vo.setIsAnonymous(review.getIsAnonymous());
        vo.setParentId(review.getParentId());
        vo.setCreatedAt(review.getCreatedAt());
        vo.setUpdatedAt(review.getUpdatedAt());

        if (Boolean.FALSE.equals(review.getIsAnonymous())) {
            User user = userMap != null ? userMap.get(review.getUserId())
                    : (review.getUserId() != null ? userRepository.selectById(review.getUserId()) : null);
            if (user != null) {
                vo.setUsername(user.getUsername());
                vo.setRealName(user.getRealName());
            }
        }
        if (review.getCourseId() != null) {
            String courseTitle = courseTitleMap != null ? courseTitleMap.get(review.getCourseId()) : null;
            if (courseTitle == null) {
                Course course = courseRepository.selectById(review.getCourseId());
                if (course != null) {
                    courseTitle = course.getTitle();
                }
            }
            vo.setCourseTitle(courseTitle);
        }
        return vo;
    }

    private java.util.Map<Long, String> buildCourseTitleMap(List<CourseReview> reviews) {
        if (reviews == null || reviews.isEmpty()) return java.util.Collections.emptyMap();
        List<Long> courseIds = reviews.stream()
                .map(CourseReview::getCourseId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        if (courseIds.isEmpty()) return java.util.Collections.emptyMap();
        return courseRepository.selectBatchIds(courseIds).stream()
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toMap(Course::getId, Course::getTitle, (a, b) -> a));
    }

    /** 批量加载评论用户 -> User Map（private方法，仅内部VO转换使用，不暴露Entity到API层） */
    private java.util.Map<Long, User> buildUserMap(List<CourseReview> reviews) {
        java.util.Set<Long> userIds = reviews.stream()
                .filter(r -> Boolean.FALSE.equals(r.getIsAnonymous()))
                .map(CourseReview::getUserId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (userIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        return userRepository.selectBatchIds(userIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, u -> u));
    }
}