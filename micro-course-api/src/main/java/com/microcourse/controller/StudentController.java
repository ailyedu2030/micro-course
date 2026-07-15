package com.microcourse.controller;

import com.microcourse.dto.EnrollmentQueryRequest;
import com.microcourse.dto.EnrollmentVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.dto.StudentDetailVO;
import com.microcourse.service.EnrollmentService;
import com.microcourse.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/students")
@Tag(name = "学员管理", description = "学员门面端点（统一入口）")
public class StudentController {

    private final EnrollmentService enrollmentService;

    public StudentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    /**
     * P3: 获取学员列表（分页，TEACHER 仅看自己课程学员，ADMIN/ACADEMIC 看全部）
     * GET /api/students
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    @Operation(summary = "获取学员列表（分页）")
    public R<PageResult<EnrollmentVO>> listStudents(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 10000) int size,
            @RequestParam(required = false) String studentName,
            @RequestParam(required = false) String courseName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String className,
            @RequestParam(required = false) String majorName) {
        EnrollmentQueryRequest query = new EnrollmentQueryRequest();
        query.setPage(page);
        query.setSize(size);
        query.setStudentName(studentName);
        query.setCourseName(courseName);
        query.setStatus(status);
        query.setClassName(className);
        query.setMajorName(majorName);
        if (SecurityUtil.hasRole("TEACHER") && !SecurityUtil.isAdmin()) {
            query.setTeacherId(SecurityUtil.getCurrentUserId());
        }
        return R.ok(enrollmentService.getEnrollmentPage(query));
    }

    /**
     * P1: 获取学员详情（统一入口）
     * GET /api/students/{userId}
     */
    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    @Operation(summary = "获取学员详情")
    public R<StudentDetailVO> getStudentDetail(@PathVariable Long userId) {
        if (SecurityUtil.hasRole("TEACHER") && !SecurityUtil.isAdmin()) {
            enrollmentService.assertStudentInTeachersCourses(SecurityUtil.getCurrentUserId(), userId);
        }
        StudentDetailVO detail = enrollmentService.getStudentDetail(userId);
        return R.ok(detail);
    }

    /**
     * P1: 获取学员学习进度（统一入口）
     * GET /api/students/{userId}/progress
     */
    @GetMapping("/{userId}/progress")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    @Operation(summary = "获取学员学习进度")
    public R<List<EnrollmentVO>> getStudentProgress(@PathVariable Long userId) {
        if (SecurityUtil.hasRole("TEACHER") && !SecurityUtil.isAdmin()) {
            enrollmentService.assertStudentInTeachersCourses(SecurityUtil.getCurrentUserId(), userId);
        }
        List<EnrollmentVO> progress = enrollmentService.getStudentProgress(userId);
        return R.ok(progress);
    }
}
