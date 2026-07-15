package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.EnrollmentVO;
import com.microcourse.entity.Course;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.User;
import com.microcourse.enums.EnrollmentStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.CourseStudentService;
import com.microcourse.service.EnrollmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class CourseStudentServiceImpl implements CourseStudentService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final EnrollmentService enrollmentService;

    public CourseStudentServiceImpl(EnrollmentRepository enrollmentRepository,
                                    CourseRepository courseRepository,
                                    UserRepository userRepository,
                                    EnrollmentService enrollmentService) {
        this.enrollmentRepository = enrollmentRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.enrollmentService = enrollmentService;
    }

    @Override
    @Transactional(readOnly = true)
    public Long findEnrollmentIdByCourseAndUser(Long courseId, Long userId) {
        Enrollment enrollment = enrollmentRepository.selectOne(
                new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getCourseId, courseId)
                        .eq(Enrollment::getUserId, userId)
                        .isNull(Enrollment::getDeletedAt));
        return enrollment != null ? enrollment.getId() : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EnrollmentVO addStudentToCourse(Long courseId, Long studentId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        User student = userRepository.selectById(studentId);
        if (student == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        Enrollment existing = enrollmentRepository.selectOne(
                new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getCourseId, courseId)
                        .eq(Enrollment::getUserId, studentId)
                        .isNull(Enrollment::getDeletedAt));
        if (existing != null) {
            if (!EnrollmentStatus.CANCELLED.getValue().equals(existing.getEnrollmentStatus())) {
                throw new BusinessException(ErrorCode.ENROLLMENT_ALREADY_EXISTS);
            }
            enrollmentRepository.deleteById(existing.getId());
        }
        Enrollment enrollment = new Enrollment();
        enrollment.setCourseId(courseId);
        enrollment.setUserId(studentId);
        enrollment.setEnrollmentStatus(EnrollmentStatus.APPROVED.getValue());
        enrollment.setProgress(0.0);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setUpdatedAt(LocalDateTime.now());
        enrollmentRepository.insert(enrollment);
        courseRepository.atomicIncrementStudentCount(courseId);
        return enrollmentService.getEnrollmentDetail(enrollment.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeStudentFromCourse(Long courseId, Long studentId) {
        Enrollment enrollment = enrollmentRepository.selectOne(
                new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getCourseId, courseId)
                        .eq(Enrollment::getUserId, studentId)
                        .isNull(Enrollment::getDeletedAt));
        if (enrollment == null) {
            throw new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND);
        }
        enrollmentService.cancelEnrollment(enrollment.getId(), null);
    }
}
