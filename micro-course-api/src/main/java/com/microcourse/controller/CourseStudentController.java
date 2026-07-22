package com.microcourse.controller;

import com.microcourse.audit.AuditedLog;
import com.microcourse.dto.R;
import com.microcourse.service.CourseStudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses")
@Tag(name = "课程学员管理", description = "课程学员添加/移除相关端点（从CourseController拆分）")
public class CourseStudentController {

    private final CourseStudentService courseStudentService;

    public CourseStudentController(CourseStudentService courseStudentService) {
        this.courseStudentService = courseStudentService;
    }

    @PostMapping("/{courseId}/students/{userId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    @AuditedLog("添加学生到课程")
    @Operation(summary = "添加学生到课程")
    public R<Void> addStudent(@PathVariable Long courseId, @PathVariable Long userId) {
        courseStudentService.addStudentToCourse(courseId, userId);
        return R.ok();
    }

    @DeleteMapping("/{courseId}/students/{userId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    @AuditedLog("从课程移除学生")
    @Operation(summary = "从课程移除学生")
    public R<Void> removeStudent(@PathVariable Long courseId, @PathVariable Long userId) {
        courseStudentService.removeStudentFromCourse(courseId, userId);
        return R.ok();
    }
}
