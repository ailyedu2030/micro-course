package com.microcourse.service;

import com.microcourse.dto.EnrollmentCreateRequest;
import com.microcourse.dto.EnrollmentUpdateRequest;
import com.microcourse.dto.EnrollmentVO;

import com.microcourse.dto.EnrollmentRankingVO;

import java.util.List;

public interface EnrollmentService {

    EnrollmentVO enroll(EnrollmentCreateRequest request);

    List<EnrollmentVO> getMyEnrollments(Long userId);

    List<EnrollmentVO> getCourseEnrollments(Long courseId);

    List<EnrollmentRankingVO> getCourseRanking(Long courseId, int limit, Long currentUserId);

    EnrollmentVO updateEnrollment(Long id, EnrollmentUpdateRequest request);

    void cancelEnrollment(Long id);
}
