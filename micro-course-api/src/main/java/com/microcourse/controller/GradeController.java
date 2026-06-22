package com.microcourse.controller;

import com.microcourse.dto.*;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.service.GradeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/grades")
public class GradeController {

    private final GradeService gradeService;

    public GradeController(GradeService gradeService) {
        this.gradeService = gradeService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<PageResult<GradeVO>> page(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long studentId,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 10000) int size) {
        return R.ok(gradeService.page(courseId, studentId, page, size));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('STUDENT')")
    public R<PageResult<GradeVO>> getMyGrades(
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 10000) int size) {
        Long userId = getCurrentUserId();
        return R.ok(gradeService.pageByStudent(userId, null, courseId, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<GradeVO> getById(@PathVariable Long id) {
        return R.ok(gradeService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<GradeVO> create(@Valid @RequestBody GradeCreateRequest request) {
        return R.ok(gradeService.create(request, getCurrentUserId()));
    }

    /**
     * 教师批改成绩 — 前端提交 enrollmentId + score + comment
     */
    @PostMapping("/teacher-grade")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<GradeVO> teacherGrade(@Valid @RequestBody GradeTeacherSubmitRequest request) {
        return R.ok(gradeService.teacherGrade(request, getCurrentUserId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<GradeVO> update(@PathVariable Long id, @Valid @RequestBody GradeUpdateRequest request) {
        return R.ok(gradeService.update(id, request, getCurrentUserId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> delete(@PathVariable Long id) {
        gradeService.delete(id);
        return R.ok();
    }

    /**
     * GET /api/grades/pending-review
     * 获取待批改的练习记录列表（仅 TEACHER/ADMIN）
     */
    @GetMapping("/pending-review")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<PageResult<ExerciseRecordVO>> getPendingReview(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 10000) int size) {
        return R.ok(gradeService.getPendingReview(page, size, getCurrentUserId()));
    }

    /**
     * POST /api/grades/{recordId}/manual-grade
     * 教师手动批改主观题
     * body: { "questionId": 123, "score": 85, "comment": "做得不错" }
     */
    @PostMapping("/{recordId}/manual-grade")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> manualGrade(@PathVariable Long recordId, @RequestBody Map<String, Object> body) {
        gradeService.manualGrade(recordId, body, getCurrentUserId());
        return R.ok();
    }

    private Long getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "未登录");
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }
        throw new BusinessException(ErrorCode.TOKEN_INVALID, "无法识别当前用户");
    }
}
