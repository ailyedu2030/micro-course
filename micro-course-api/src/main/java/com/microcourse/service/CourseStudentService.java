package com.microcourse.service;

import com.microcourse.dto.EnrollmentVO;

public interface CourseStudentService {

    Long findEnrollmentIdByCourseAndUser(Long courseId, Long userId);

    EnrollmentVO addStudentToCourse(Long courseId, Long studentId);

    void removeStudentFromCourse(Long courseId, Long studentId);
}
