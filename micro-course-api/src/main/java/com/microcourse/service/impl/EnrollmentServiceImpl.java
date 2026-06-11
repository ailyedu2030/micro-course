package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.EnrollmentCreateRequest;
import com.microcourse.dto.EnrollmentUpdateRequest;
import com.microcourse.dto.EnrollmentVO;
import com.microcourse.entity.Course;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.EnrollmentService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EnrollmentServiceImpl implements EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    public EnrollmentServiceImpl(EnrollmentRepository enrollmentRepository,
                                 CourseRepository courseRepository,
                                 UserRepository userRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public EnrollmentVO enroll(EnrollmentCreateRequest request) {
        // 幂等性优先检查：已选过则直接返回
        LambdaQueryWrapper<Enrollment> existingWrapper = new LambdaQueryWrapper<>();
        existingWrapper.eq(Enrollment::getUserId, request.getUserId())
                .eq(Enrollment::getCourseId, request.getCourseId());
        Enrollment existingEnrollment = enrollmentRepository.selectOne(existingWrapper);
        if (existingEnrollment != null) {
            return convertToVO(existingEnrollment);
        }

        // Check course exists
        Course course = courseRepository.selectById(request.getCourseId());
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        // Check user exists
        User user = userRepository.selectById(request.getUserId());
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        Enrollment enrollment = new Enrollment();
        enrollment.setCourseId(request.getCourseId());
        enrollment.setUserId(request.getUserId());
        enrollment.setSourceChannel(request.getSourceChannel());
        enrollment.setEnrollmentStatus("PENDING");
        enrollment.setProgress(0.0);
        enrollment.setCompleted(false);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setUpdatedAt(LocalDateTime.now());

        enrollmentRepository.insert(enrollment);
        return convertToVO(enrollment);
    }

    @Override
    public List<EnrollmentVO> getMyEnrollments(Long userId) {
        LambdaQueryWrapper<Enrollment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Enrollment::getUserId, userId)
                .orderByDesc(Enrollment::getEnrolledAt);
        List<Enrollment> enrollments = enrollmentRepository.selectList(wrapper);
        return convertToVOList(enrollments);
    }

    @Override
    public List<EnrollmentVO> getCourseEnrollments(Long courseId) {
        LambdaQueryWrapper<Enrollment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Enrollment::getCourseId, courseId)
                .orderByDesc(Enrollment::getEnrolledAt);
        List<Enrollment> enrollments = enrollmentRepository.selectList(wrapper);
        return convertToVOList(enrollments);
    }

    private List<EnrollmentVO> convertToVOList(List<Enrollment> enrollments) {
        if (enrollments.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        // N+1 修复：批量预加载 course 和 user
        java.util.Set<Long> courseIds = enrollments.stream()
                .map(Enrollment::getCourseId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Set<Long> userIds = enrollments.stream()
                .map(Enrollment::getUserId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        java.util.Map<Long, Course> courseMap = new java.util.HashMap<>();
        java.util.Map<Long, User> userMap = new java.util.HashMap<>();

        if (!courseIds.isEmpty()) {
            courseRepository.selectBatchIds(courseIds).forEach(c -> courseMap.put(c.getId(), c));
        }
        if (!userIds.isEmpty()) {
            userRepository.selectBatchIds(userIds).forEach(u -> userMap.put(u.getId(), u));
        }

        final java.util.Map<Long, Course> finalCourseMap = courseMap;
        final java.util.Map<Long, User> finalUserMap = userMap;

        return enrollments.stream()
                .map(e -> convertToVO(e, finalCourseMap, finalUserMap))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EnrollmentVO updateEnrollment(Long id, EnrollmentUpdateRequest request) {
        Enrollment enrollment = enrollmentRepository.selectById(id);
        if (enrollment == null) {
            throw new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND);
        }

        if (request.getProgress() != null) {
            enrollment.setProgress(request.getProgress());
        }
        if (request.getCompleted() != null) {
            enrollment.setCompleted(request.getCompleted());
            if (request.getCompleted()) {
                enrollment.setCompletedAt(LocalDateTime.now());
            }
        }
        if (request.getFinalScore() != null) {
            enrollment.setFinalScore(request.getFinalScore());
        }
        if (request.getFinalGrade() != null) {
            enrollment.setFinalGrade(request.getFinalGrade());
        }
        if (request.getEnrollmentStatus() != null) {
            enrollment.setEnrollmentStatus(request.getEnrollmentStatus());
        }

        enrollment.setUpdatedAt(LocalDateTime.now());
        enrollmentRepository.updateById(enrollment);
        return convertToVO(enrollment);
    }

    @Override
    @Transactional
    public void cancelEnrollment(Long id) {
        Enrollment enrollment = enrollmentRepository.selectById(id);
        if (enrollment == null) {
            throw new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND);
        }
        // IDOR 校验：仅本人或 ADMIN 可取消
        Long currentUserId = getCurrentUserId();
        boolean isAdmin = hasAdminRole();
        if (!isAdmin && !enrollment.getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        enrollment.setEnrollmentStatus("CANCELLED");
        enrollment.setUpdatedAt(LocalDateTime.now());
        enrollmentRepository.updateById(enrollment);
    }

    private EnrollmentVO convertToVO(Enrollment enrollment) {
        EnrollmentVO vo = new EnrollmentVO();
        vo.setId(enrollment.getId());
        vo.setCourseId(enrollment.getCourseId());
        vo.setUserId(enrollment.getUserId());
        vo.setProgress(enrollment.getProgress());
        vo.setCompleted(enrollment.getCompleted());
        vo.setFinalScore(enrollment.getFinalScore());
        vo.setFinalGrade(enrollment.getFinalGrade());
        vo.setEnrollmentStatus(enrollment.getEnrollmentStatus());
        vo.setSourceChannel(enrollment.getSourceChannel());
        vo.setEnrolledAt(enrollment.getEnrolledAt());
        vo.setCompletedAt(enrollment.getCompletedAt());
        vo.setUpdatedAt(enrollment.getUpdatedAt());

        // Load course name
        if (enrollment.getCourseId() != null) {
            Course course = courseRepository.selectById(enrollment.getCourseId());
            if (course != null) {
                vo.setCourseName(course.getTitle());
            }
        }

        // Load user name
        if (enrollment.getUserId() != null) {
            User user = userRepository.selectById(enrollment.getUserId());
            if (user != null) {
                vo.setUserName(user.getRealName());
            }
        }

        return vo;
    }

    private EnrollmentVO convertToVO(Enrollment enrollment, java.util.Map<Long, Course> courseMap,
                                     java.util.Map<Long, User> userMap) {
        EnrollmentVO vo = new EnrollmentVO();
        vo.setId(enrollment.getId());
        vo.setCourseId(enrollment.getCourseId());
        vo.setUserId(enrollment.getUserId());
        vo.setProgress(enrollment.getProgress());
        vo.setCompleted(enrollment.getCompleted());
        vo.setFinalScore(enrollment.getFinalScore());
        vo.setFinalGrade(enrollment.getFinalGrade());
        vo.setEnrollmentStatus(enrollment.getEnrollmentStatus());
        vo.setSourceChannel(enrollment.getSourceChannel());
        vo.setEnrolledAt(enrollment.getEnrolledAt());
        vo.setCompletedAt(enrollment.getCompletedAt());
        vo.setUpdatedAt(enrollment.getUpdatedAt());

        // Load course name（使用预加载的 Map）
        if (enrollment.getCourseId() != null) {
            Course course = courseMap.get(enrollment.getCourseId());
            if (course != null) {
                vo.setCourseName(course.getTitle());
            }
        }

        // Load user name（使用预加载的 Map）
        if (enrollment.getUserId() != null) {
            User user = userMap.get(enrollment.getUserId());
            if (user != null) {
                vo.setUserName(user.getRealName());
            }
        }

        return vo;
    }

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Long) return (Long) principal;
        throw new BusinessException(ErrorCode.TOKEN_INVALID);
    }

    private boolean hasAdminRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        for (GrantedAuthority granted : auth.getAuthorities()) {
            if (granted.getAuthority().equals("ROLE_ADMIN")) return true;
        }
        return false;
    }
}
