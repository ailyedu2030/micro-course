package com.microcourse.service;

import com.microcourse.dto.EnrollmentQueryRequest;
import com.microcourse.dto.EnrollmentRankingVO;
import com.microcourse.dto.EnrollmentVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.StudentDetailVO;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.http.HttpServletResponse;

public interface EnrollmentQueryService {

    PageResult<EnrollmentVO> getEnrollmentPage(EnrollmentQueryRequest query);

    PageResult<EnrollmentVO> getCourseEnrollmentPage(Long courseId, int page, int size);

    List<EnrollmentVO> getCourseEnrollments(Long courseId);

    List<EnrollmentRankingVO> getCourseRanking(Long courseId, int limit, Long currentUserId);

    StudentDetailVO getStudentDetail(Long userId);

    List<EnrollmentVO> getMyEnrollments(Long userId, Boolean completed);

    void exportEnrollments(Long courseId, HttpServletResponse response) throws IOException;
}
