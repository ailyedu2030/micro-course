package com.microcourse.service;

import com.microcourse.dto.EnrollmentQueryRequest;
import com.microcourse.dto.EnrollmentRankingVO;
import com.microcourse.dto.EnrollmentVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.StudentDetailVO;

import java.util.List;

public interface EnrollmentQueryService {

    PageResult<EnrollmentVO> getEnrollmentPage(EnrollmentQueryRequest query);

    PageResult<EnrollmentVO> getCourseEnrollmentPage(Long courseId, int page, int size);

    List<EnrollmentVO> getCourseEnrollments(Long courseId);

    List<EnrollmentRankingVO> getCourseRanking(Long courseId, int limit, Long currentUserId);

    StudentDetailVO getStudentDetail(Long userId);

    List<EnrollmentVO> getMyEnrollments(Long userId, Boolean completed);
}
