package com.microcourse.service;

import com.microcourse.dto.EnrollmentVO;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

public interface CourseStudentService {

    Long findEnrollmentIdByCourseAndUser(Long courseId, Long userId);

    EnrollmentVO addStudentToCourse(Long courseId, Long studentId);

    void removeStudentFromCourse(Long courseId, Long studentId);
}
