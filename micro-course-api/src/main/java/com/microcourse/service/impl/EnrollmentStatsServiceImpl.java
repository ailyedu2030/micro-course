package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.entity.Course;
import com.microcourse.entity.Enrollment;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.service.EnrollmentStatsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EnrollmentStatsServiceImpl implements EnrollmentStatsService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;

    public EnrollmentStatsServiceImpl(EnrollmentRepository enrollmentRepository,
                                       CourseRepository courseRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.courseRepository = courseRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public long countByTeacherId(Long teacherId) {
        Set<Long> courseIds = getCourseIdsByTeacherId(teacherId);
        if (courseIds.isEmpty()) {
            return 0;
        }
        return enrollmentRepository.selectCount(
            new LambdaQueryWrapper<Enrollment>()
                .in(Enrollment::getCourseId, courseIds)
                .isNull(Enrollment::getDeletedAt));
    }

    @Override
    @Transactional(readOnly = true)
    public long countCompletedByTeacherId(Long teacherId) {
        Set<Long> courseIds = getCourseIdsByTeacherId(teacherId);
        if (courseIds.isEmpty()) {
            return 0;
        }
        return enrollmentRepository.selectCount(
            new LambdaQueryWrapper<Enrollment>()
                .in(Enrollment::getCourseId, courseIds)
                .eq(Enrollment::getCompleted, true)
                .isNull(Enrollment::getDeletedAt));
    }

    @Override
    @Transactional(readOnly = true)
    public double getAvgScoreByTeacherId(Long teacherId) {
        Double avg = enrollmentRepository.avgScoreByTeacherId(teacherId);
        return avg != null ? avg : 0;
    }

    private Set<Long> getCourseIdsByTeacherId(Long teacherId) {
        return courseRepository.selectList(
                new LambdaQueryWrapper<Course>()
                    .select(Course::getId)
                    .eq(Course::getTeacherId, teacherId)
                    .isNull(Course::getDeletedAt))
            .stream()
            .map(Course::getId)
            .collect(Collectors.toSet());
    }
}
