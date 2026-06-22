package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.CourseReviewRequest;
import com.microcourse.dto.CourseReviewVO;
import com.microcourse.dto.PageResult;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        // 验证用户已选修该课程
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

        // 检查是否已评价（每人每课程只能评价一次）
        LambdaQueryWrapper<CourseReview> reviewWrapper = new LambdaQueryWrapper<>();
        reviewWrapper.eq(CourseReview::getUserId, userId)
                .eq(CourseReview::getCourseId, courseId);
        CourseReview existing = courseReviewRepository.selectOne(reviewWrapper);
        if (existing != null) {
            throw new BusinessException(ErrorCode.COURSE_REVIEW_ALREADY_EXISTS);
        }

        CourseReview review = new CourseReview();
        review.setCourseId(courseId);
        review.setUserId(userId);
        review.setRating(request.getRating());
        review.setContent(request.getContent());
        review.setIsAnonymous(request.getIsAnonymous() != null ? request.getIsAnonymous() : false);
        review.setCreatedAt(LocalDateTime.now());
        review.setUpdatedAt(LocalDateTime.now());

        try {
            courseReviewRepository.insert(review);
        } catch (org.springframework.dao.DuplicateKeyException dupEx) {
            // CON-NEW-7 修复:DB uk_course_reviews_user_course 兜底,降级为业务异常
            throw new BusinessException(ErrorCode.COURSE_REVIEW_ALREADY_EXISTS);
        }
        updateCourseAvgRating(courseId);
        return convertToVO(review);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CourseReviewVO> listByCourse(Long courseId, int page, int size) {
        Page<CourseReview> pg = new Page<>(page + 1, size);
        LambdaQueryWrapper<CourseReview> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseReview::getCourseId, courseId)
                .orderByDesc(CourseReview::getCreatedAt);
        IPage<CourseReview> result = courseReviewRepository.selectPage(pg, wrapper);
        java.util.Map<Long, User> userMap = buildUserMap(result.getRecords());
        return PageResult.of(result.getRecords().stream()
                .map(r -> convertToVO(r, userMap))
                .collect(Collectors.toList()), result.getTotal(), page, size);
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
        return PageResult.of(result.getRecords().stream()
                .map(r -> convertToVO(r, userMap))
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
        return PageResult.of(result.getRecords().stream()
                .map(r -> convertToVO(r, userMap))
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
        CourseReviewVO vo = new CourseReviewVO();
        vo.setId(review.getId());
        vo.setCourseId(review.getCourseId());
        vo.setUserId(review.getUserId());
        vo.setRating(review.getRating());
        vo.setContent(review.getContent());
        vo.setIsAnonymous(review.getIsAnonymous());
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
        return vo;
    }

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