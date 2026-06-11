package com.microcourse.service;

import com.microcourse.dto.EnrollmentCreateRequest;
import com.microcourse.dto.EnrollmentUpdateRequest;
import com.microcourse.dto.EnrollmentVO;

import java.util.List;

public interface EnrollmentService {

    EnrollmentVO enroll(EnrollmentCreateRequest request);

    List<EnrollmentVO> getMyEnrollments(Long userId);

    List<EnrollmentVO> getCourseEnrollments(Long courseId);

    EnrollmentVO updateEnrollment(Long id, EnrollmentUpdateRequest request);

    void cancelEnrollment(Long id);
}
