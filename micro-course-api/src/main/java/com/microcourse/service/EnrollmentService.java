package com.microcourse.service;

import com.microcourse.dto.EnrollmentCreateRequest;
import com.microcourse.dto.EnrollmentQueryRequest;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.EnrollmentUpdateRequest;
import com.microcourse.dto.EnrollmentVO;
import com.microcourse.dto.StudentDetailVO;

import com.microcourse.dto.EnrollmentRankingVO;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.http.HttpServletResponse;

public interface EnrollmentService {

    EnrollmentVO enroll(EnrollmentCreateRequest request);

    List<EnrollmentVO> getMyEnrollments(Long userId, Boolean completed);

    PageResult<EnrollmentVO> getEnrollmentPage(EnrollmentQueryRequest query);

    /** P1-2: 课程学员分页查询 */
    PageResult<EnrollmentVO> getCourseEnrollmentPage(Long courseId, int page, int size);

    /** 保留全量查询（供导出使用） */
    List<EnrollmentVO> getCourseEnrollments(Long courseId);

    /**
     * 获取课程学生列表, 含 Owner 校验 (Controller 调用)
     * - TEACHER 非 ADMIN 必须为课程 owner, 否则 NO_PERMISSION
     * - ADMIN / ACADEMIC 跳过校验
     */
    List<EnrollmentVO> getCourseEnrollmentsWithOwnerCheck(Long courseId);

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

    /**
     * Phase A-4 (P0-5): 获取单条选课详情。
     * 仅负责数据装配，按角色的权限校验（本人/课主/ADMIN/ACADEMIC）在 Controller 层执行。
     * @param id 选课记录ID
     * @return 选课详情VO
     */
    EnrollmentVO getEnrollmentDetail(Long id);

    /**
     * 查询某课程下所有活跃选课学生的 userId 列表。
     * 供通知/批量操作使用。
     * @param courseId 课程 ID
     * @return userId 列表
     */
    List<Long> findActiveUserIdsByCourseId(Long courseId);

    /**
     * R12 P1-C-4: 校验某学生是否在指定教师授课课程中。
     * 用于 Controller 层数据隔离：TEACHER 仅能查询自己课程中的学生详情。
     * @param teacherId 教师 ID
     * @param studentId 学生 ID
     * @throws BusinessException 若该学生不在教师授课课程中
     */
    void assertStudentInTeachersCourses(Long teacherId, Long studentId);

    /**
     * 导出课程学员数据为 Excel（包含 TEACHER 角色课程所有权校验）
     * @param courseId 课程ID
     * @param response HTTP 响应
     * @throws IOException 写入异常
     */
    void exportEnrollments(Long courseId, HttpServletResponse response) throws IOException;

    /**
     * P1-I-6: 候补自动晋升（独立事务 REQUIRES_NEW）。
     * 当有学生退课腾出名额时,自动将候补队列中最早的一个学生从 WAITLIST 转为 APPROVED。
     * 独立事务确保晋升失败不影响原 cancel 事务。
     * @param courseId 课程ID
     */
    void promoteFirstWaitlistToEnrolled(Long courseId);

    /**
     * P1: 获取学员学习进度（所有课程的选课进度）
     * @param userId 学员ID
     * @return 选课列表（含 progress、completed 等学习进度）
     */
    List<EnrollmentVO> getStudentProgress(Long userId);
}
