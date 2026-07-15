package com.microcourse.controller;

import com.microcourse.audit.AuditedLog;
import com.microcourse.dto.EnrollmentCreateRequest;
import com.microcourse.dto.EnrollmentQueryRequest;
import com.microcourse.dto.EnrollmentRankingVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.EnrollmentUpdateRequest;
import com.microcourse.dto.EnrollmentVO;
import com.microcourse.dto.R;
import com.microcourse.dto.StudentDetailVO;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.service.EnrollmentService;
import com.microcourse.util.SecurityUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.io.IOException;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/enrollments")
@Validated
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    @AuditedLog("创建选课")
    public R<EnrollmentVO> enroll(@Valid @RequestBody EnrollmentCreateRequest request) {
        // P0-06 修复：禁止客户端直接传入 PAYMENT sourceChannel
        if ("PAYMENT".equals(request.getSourceChannel())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "非法请求来源");
        }
        Long userId = SecurityUtil.getCurrentUserId();
        request.setUserId(userId);
        EnrollmentVO vo = enrollmentService.enroll(request);
        return R.ok(vo);
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public R<List<EnrollmentVO>> getMyEnrollments(
            @RequestParam(required = false) Boolean completed) {
        Long userId = SecurityUtil.getCurrentUserId();
        List<EnrollmentVO> list = enrollmentService.getMyEnrollments(userId, completed);
        return R.ok(list);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<PageResult<EnrollmentVO>> getEnrollments(
            @RequestParam(defaultValue = "0") @PositiveOrZero Integer page,
            @RequestParam(defaultValue = "10") @Range(min = 1, max = 1000) Integer size,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) String studentName,
            @RequestParam(required = false) String courseName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String className,
            @RequestParam(required = false) String majorName) {
        EnrollmentQueryRequest query = new EnrollmentQueryRequest();
        query.setPage(page);
        query.setSize(size);
        query.setTeacherId(teacherId);
        query.setStudentName(studentName);
        query.setCourseName(courseName);
        query.setStatus(status);
        query.setClassName(className);
        query.setMajorName(majorName);
        PageResult<EnrollmentVO> result = enrollmentService.getEnrollmentPage(query);
        return R.ok(result);
    }

    /** P1-1: 收紧权限仅 TEACHER/ADMIN；P1-2: 返回分页结果 */
    @GetMapping("/course/{courseId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<PageResult<EnrollmentVO>> getCourseEnrollments(
            @PathVariable Long courseId,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "10") @Range(min = 1, max = 100) int size) {
        PageResult<EnrollmentVO> result = enrollmentService.getCourseEnrollmentPage(courseId, page, size);
        return R.ok(result);
    }

    /** P0-2: 获取学员详情（关联 users + classes + majors） */
    @GetMapping("/student-detail/{userId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<StudentDetailVO> getStudentDetail(@PathVariable Long userId) {
        // R12 P1-C-4: TEACHER 仅能查询自己课程中的学生（校验由 Service 层执行）
        if (SecurityUtil.hasRole("TEACHER") && !SecurityUtil.isAdmin()) {
            enrollmentService.assertStudentInTeachersCourses(SecurityUtil.getCurrentUserId(), userId);
        }
        StudentDetailVO detail = enrollmentService.getStudentDetail(userId);
        return R.ok(detail);
    }

    /**
     * P1: 获取学员学习进度（所有课程的选课进度）
     * @param userId 学员ID
     * @return 选课列表（含 progress、completed 等学习进度）
     */
    @GetMapping("/student/{userId}/progress")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<List<EnrollmentVO>> getStudentProgress(@PathVariable Long userId) {
        if (SecurityUtil.hasRole("TEACHER") && !SecurityUtil.isAdmin()) {
            enrollmentService.assertStudentInTeachersCourses(SecurityUtil.getCurrentUserId(), userId);
        }
        List<EnrollmentVO> progress = enrollmentService.getStudentProgress(userId);
        return R.ok(progress);
    }

    @GetMapping("/course/{courseId}/ranking")
    @PreAuthorize("isAuthenticated()")
    public R<List<EnrollmentRankingVO>> getCourseRanking(
            @PathVariable Long courseId,
            @RequestParam(defaultValue = "10") int limit) {
        Long userId = SecurityUtil.getCurrentUserId();
        List<EnrollmentRankingVO> ranking = enrollmentService.getCourseRanking(courseId, limit, userId);
        return R.ok(ranking);
    }

    /**
     * GET /api/enrollments/{id}
     * 获取选课详情（Phase A-4 P0-5 新增）
     * 权限校验（角色级）已下沉至 Service 层：
     * - ADMIN / ACADEMIC：无限制
     * - TEACHER：必须为该选课所属课程的 owner
     * - STUDENT：仅本人选课
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN','ACADEMIC')")
    public R<EnrollmentVO> getEnrollmentDetail(@PathVariable Long id) {
        EnrollmentVO vo = enrollmentService.getEnrollmentDetail(id);
        return R.ok(vo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<EnrollmentVO> updateEnrollment(@PathVariable Long id,
                                            @Valid @RequestBody EnrollmentUpdateRequest request) {
        EnrollmentVO vo = enrollmentService.updateEnrollment(id, request);
        return R.ok(vo);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public R<Void> cancelEnrollment(@PathVariable Long id) {
        // R1-SEC-001 修复:@PreAuthorize SpEL 不能用 #id(enrollment ID) == authentication.principal(user ID)
        // 因 enrollment.id 不等于 user.id,会导致合法用户也无法取消。IDOR 校验全部下沉到 Service 层
        Long currentUserId = SecurityUtil.getCurrentUserId();
        enrollmentService.cancelEnrollment(id, currentUserId);
        return R.ok();
    }

    /**
     * GET /api/enrollments/export
     * 导出课程学员数据为 Excel（权限校验与导出逻辑已下沉至 Service 层）
     * @param courseId 课程ID
     */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public void exportEnrollments(
            @RequestParam Long courseId,
            HttpServletResponse response) throws IOException {
        enrollmentService.exportEnrollments(courseId, response);
    }

}