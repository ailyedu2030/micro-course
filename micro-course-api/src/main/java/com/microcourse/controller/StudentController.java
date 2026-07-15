package com.microcourse.controller;

import com.microcourse.dto.EnrollmentVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.dto.StudentDetailVO;
import com.microcourse.service.EnrollmentService;
import com.microcourse.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
