package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.CourseReviewCreateRequest;
import com.microcourse.dto.CourseReviewVO;
import com.microcourse.dto.PageResult;
import com.microcourse.entity.CourseReview;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseReviewRepository;
import com.microcourse.repository.EnrollmentRepository;
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

    public CourseReviewServiceImpl(CourseReviewRepository courseReviewRepository,
                                   EnrollmentRepository enrollmentRepository,
                                   UserRepository userRepository) {
        this.courseReviewRepository = courseReviewRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public CourseReviewVO create(CourseReviewCreateRequest request, Long userId) {
        // 验证用户已选修该课程
        LambdaQueryWrapper<Enrollment> enrollWrapper = new LambdaQueryWrapper<>();
        enrollWrapper.eq(Enrollment::getUserId, userId)
                .eq(Enrollment::getCourseId, request.getCourseId());
        Enrollment enrollment = enrollmentRepository.selectOne(enrollWrapper);
        if (enrollment == null) {
            throw new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND);
        }

        // 验证评分范围 1-5
        if (request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
            throw new BusinessException(ErrorCode.COURSE_REVIEW_INVALID_RATING);
        }

        // 检查是否已评价（每人每课程只能评价一次）
        LambdaQueryWrapper<CourseReview> reviewWrapper = new LambdaQueryWrapper<>();
        reviewWrapper.eq(CourseReview::getUserId, userId)
                .eq(CourseReview::getCourseId, request.getCourseId());
        CourseReview existing = courseReviewRepository.selectOne(reviewWrapper);
        if (existing != null) {
            throw new BusinessException(ErrorCode.COURSE_REVIEW_ALREADY_EXISTS);
        }

        CourseReview review = new CourseReview();
        review.setCourseId(request.getCourseId());
        review.setUserId(userId);
        review.setRating(request.getRating());
        review.setContent(request.getContent());
        review.setIsAnonymous(request.getIsAnonymous() != null ? request.getIsAnonymous() : false);
        review.setCreatedAt(LocalDateTime.now());
        review.setUpdatedAt(LocalDateTime.now());

        courseReviewRepository.insert(review);
        return convertToVO(review);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CourseReviewVO> listByCourse(Long courseId, int page, int size) {
        Page<CourseReview> pg = new Page<>(page + 1, size); // MyBatis-Plus 1-based
        LambdaQueryWrapper<CourseReview> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseReview::getCourseId, courseId)
                .orderByDesc(CourseReview::getCreatedAt);
        IPage<CourseReview> result = courseReviewRepository.selectPage(pg, wrapper);
        return PageResult.of(result.getRecords().stream()
                .map(this::convertToVO)
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
        return PageResult.of(result.getRecords().stream()
                .map(this::convertToVO)
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
        return PageResult.of(result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList()), result.getTotal(), page, size);
    }

    @Override
    @Transactional
    public void deleteReview(Long id) {
        CourseReview review = courseReviewRepository.selectById(id);
        if (review == null) {
            throw new BusinessException(ErrorCode.COURSE_REVIEW_NOT_FOUND);
        }
        courseReviewRepository.deleteById(id);
    }

    private CourseReviewVO convertToVO(CourseReview review) {
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
            User user = userRepository.selectById(review.getUserId());
            if (user != null) {
                vo.setUsername(user.getUsername());
                vo.setRealName(user.getRealName());
            }
        }
        return vo;
    }
}