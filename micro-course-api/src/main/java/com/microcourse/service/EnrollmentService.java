package com.microcourse.service;

import com.microcourse.dto.EnrollmentCreateRequest;
import com.microcourse.dto.EnrollmentQueryRequest;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.EnrollmentUpdateRequest;
import com.microcourse.dto.EnrollmentVO;
import com.microcourse.dto.StudentDetailVO;

import com.microcourse.dto.EnrollmentRankingVO;

import java.util.List;

public interface EnrollmentService {

    EnrollmentVO enroll(EnrollmentCreateRequest request);

    List<EnrollmentVO> getMyEnrollments(Long userId, Boolean completed);

    PageResult<EnrollmentVO> getEnrollmentPage(EnrollmentQueryRequest query);

    /** P1-2: 课程学员分页查询 */
    PageResult<EnrollmentVO> getCourseEnrollmentPage(Long courseId, int page, int size);

    /** 保留全量查询（供导出使用） */
    List<EnrollmentVO> getCourseEnrollments(Long courseId);

    List<EnrollmentRankingVO> getCourseRanking(Long courseId, int limit, Long currentUserId);

    EnrollmentVO updateEnrollment(Long id, EnrollmentUpdateRequest request);

    void cancelEnrollment(Long id, Long currentUserId);

    long countByTeacherId(Long teacherId);

    long countCompletedByTeacherId(Long teacherId);

    double getAvgScoreByTeacherId(Long teacherId);

    /** P0-2: 获取学员详情（关联 users + classes + majors） */
    StudentDetailVO getStudentDetail(Long userId);

    /** 校验当前教师是否为课程 Owner（TEACHER 角色专用） */
    void assertCourseOwnership(Long courseId);
}
