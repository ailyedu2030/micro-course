package com.microcourse.controller;

import com.microcourse.dto.EnrollmentCreateRequest;
import com.microcourse.dto.EnrollmentQueryRequest;
import com.microcourse.dto.EnrollmentRankingVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.EnrollmentUpdateRequest;
import com.microcourse.dto.EnrollmentVO;
import com.microcourse.dto.R;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.service.EnrollmentService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.io.IOException;
import jakarta.servlet.http.HttpServletResponse;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;

@RestController
@RequestMapping("/api/enrollments")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
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
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String studentName,
            @RequestParam(required = false) String courseName,
            @RequestParam(required = false) String status) {
        EnrollmentQueryRequest query = new EnrollmentQueryRequest();
        query.setPage(page);
        query.setSize(size);
        query.setStudentName(studentName);
        query.setCourseName(courseName);
        query.setStatus(status);
        PageResult<EnrollmentVO> result = enrollmentService.getEnrollmentPage(query);
        return R.ok(result);
    }

    @GetMapping("/course/{courseId}")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    public R<List<EnrollmentVO>> getCourseEnrollments(@PathVariable Long courseId) {
        List<EnrollmentVO> list = enrollmentService.getCourseEnrollments(courseId);
        return R.ok(list);
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

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<EnrollmentVO> updateEnrollment(@PathVariable Long id,
                                            @Valid @RequestBody EnrollmentUpdateRequest request) {
        EnrollmentVO vo = enrollmentService.updateEnrollment(id, request);
        return R.ok(vo);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN','ACADEMIC')")
    public R<Void> cancelEnrollment(@PathVariable Long id) {
        enrollmentService.cancelEnrollment(id);
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

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Long) return (Long) principal;
        return null;
    }
}