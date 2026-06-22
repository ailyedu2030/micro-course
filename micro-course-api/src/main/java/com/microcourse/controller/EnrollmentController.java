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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.io.IOException;
import jakarta.servlet.http.HttpServletResponse;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;

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
        Long userId = getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        request.setUserId(userId);
        EnrollmentVO vo = enrollmentService.enroll(request);
        return R.ok(vo);
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public R<List<EnrollmentVO>> getMyEnrollments(
            @RequestParam(required = false) Boolean completed) {
        Long userId = getCurrentUserId();
        List<EnrollmentVO> list = enrollmentService.getMyEnrollments(userId, completed);
        return R.ok(list);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<PageResult<EnrollmentVO>> getEnrollments(
            @RequestParam(required = false) @PositiveOrZero Integer page,
            @RequestParam(required = false) @Range(min = 1, max = 10000) Integer size,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) String studentName,
            @RequestParam(required = false) String courseName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String className,
            @RequestParam(required = false) String majorName) {
        // SECURITY: TEACHER 只能查自己课程的学员，强制覆写 teacherId
        if (hasRole("TEACHER")) {
            teacherId = getCurrentUserId();
        }
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
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<PageResult<EnrollmentVO>> getCourseEnrollments(
            @PathVariable Long courseId,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "10") @Range(min = 1, max = 10000) int size) {
        // SECURITY: TEACHER 必须为课程 owner
        if (hasRole("TEACHER")) {
            enrollmentService.assertCourseOwnership(courseId);
        }
        PageResult<EnrollmentVO> result = enrollmentService.getCourseEnrollmentPage(courseId, page, size);
        return R.ok(result);
    }

    /** P0-2: 获取学员详情（关联 users + classes + majors） */
    @GetMapping("/student-detail/{userId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<StudentDetailVO> getStudentDetail(@PathVariable Long userId) {
        StudentDetailVO detail = enrollmentService.getStudentDetail(userId);
        return R.ok(detail);
    }

    @GetMapping("/course/{courseId}/ranking")
    @PreAuthorize("isAuthenticated()")
    public R<List<EnrollmentRankingVO>> getCourseRanking(
            @PathVariable Long courseId,
            @RequestParam(defaultValue = "10") int limit) {
        Long userId = getCurrentUserId();
        List<EnrollmentRankingVO> ranking = enrollmentService.getCourseRanking(courseId, limit, userId);
        return R.ok(ranking);
    }

    /**
     * GET /api/enrollments/{id}
     * 获取选课详情（Phase A-4 P0-5 新增）
     * 权限：STUDENT(本人) / TEACHER(课程创建者) / ADMIN / ACADEMIC ——
     *      依据 权限矩阵 v2.0 §2.8 READ_ENROLLMENT_DETAIL。
     * - ADMIN / ACADEMIC：无限制
     * - TEACHER：必须为该选课所属课程的 owner（assertCourseOwnership，非 owner → 403）
     * - STUDENT：仅本人选课（IDOR 校验，非本人 → 403）
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN','ACADEMIC')")
    public R<EnrollmentVO> getEnrollmentDetail(@PathVariable Long id) {
        EnrollmentVO vo = enrollmentService.getEnrollmentDetail(id);
        if (SecurityUtil.isAdmin() || SecurityUtil.hasRole("ACADEMIC")) {
            return R.ok(vo);
        }
        if (SecurityUtil.hasRole("TEACHER")) {
            // TEACHER 必须为课程 owner，否则抛 NO_PERMISSION(403)
            enrollmentService.assertCourseOwnership(vo.getCourseId());
            return R.ok(vo);
        }
        // STUDENT：仅本人
        Long currentUserId = getCurrentUserId();
        if (vo.getUserId() == null || !vo.getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        return R.ok(vo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
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
     * 导出课程学员数据为 Excel
     * @param courseId 课程ID
     */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public void exportEnrollments(
            @RequestParam Long courseId,
            HttpServletResponse response) throws IOException {
        // P0-SEC-FIX: TEACHER 角色添加课程所有权校验，防止 IDOR 导出任意课程数据
        if (hasRole("TEACHER")) {
            enrollmentService.assertCourseOwnership(courseId);
        }
        List<EnrollmentVO> enrollments = enrollmentService.getCourseEnrollments(courseId);

        // 设置响应头
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=enrollments_" + courseId + ".xlsx");

        // 使用 Hutool ExcelWriter 导出
        ExcelWriter writer = ExcelUtil.getWriter(true);
        writer.addHeaderAlias("id", "选课ID");
        writer.addHeaderAlias("courseId", "课程ID");
        writer.addHeaderAlias("courseName", "课程名称");
        writer.addHeaderAlias("userId", "用户ID");
        writer.addHeaderAlias("userName", "学生姓名");
        writer.addHeaderAlias("progress", "学习进度(%)");
        writer.addHeaderAlias("completed", "是否完成");
        writer.addHeaderAlias("finalScore", "总评成绩");
        writer.addHeaderAlias("finalGrade", "成绩等级");
        writer.addHeaderAlias("enrollmentStatus", "选课状态");
        writer.addHeaderAlias("sourceChannel", "选课来源");
        writer.addHeaderAlias("enrolledAt", "选课时间");
        writer.addHeaderAlias("completedAt", "完成时间");

        writer.write(enrollments, true);
        writer.flush(response.getOutputStream());
        writer.close();
    }

    /** P1-3: getCurrentUserId 类型安全 —— 兼容 Long / String / Number 类型 principal */
    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Long) return (Long) principal;
        if (principal instanceof Number) return ((Number) principal).longValue();
        if (principal instanceof String str) {
            try { return Long.parseLong(str); } catch (NumberFormatException ignored) { /* fall through */ }
        }
        throw new BusinessException(ErrorCode.TOKEN_INVALID);
    }

    /** 检查当前用户是否拥有指定角色 */
    private boolean hasRole(String role) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(g -> g.getAuthority().equals("ROLE_" + role));
    }
}