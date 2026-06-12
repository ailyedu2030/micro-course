package com.microcourse.controller;

import com.microcourse.dto.*;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.service.TeacherService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teacher")
public class TeacherController {

    private final TeacherService teacherService;

    public TeacherController(TeacherService teacherService) {
        this.teacherService = teacherService;
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<TeacherStatsVO> getStats() {
        return R.ok(teacherService.getStats(getCurrentUserId()));
    }

    @GetMapping("/student-activity")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<List<StudentActivityVO>> getStudentActivity(@RequestParam(defaultValue = "7") int days) {
        return R.ok(teacherService.getStudentActivity(getCurrentUserId(), days));
    }

    @GetMapping("/pending-tasks")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<List<PendingTaskVO>> getPendingTasks(@RequestParam(defaultValue = "5") int size) {
        return R.ok(teacherService.getPendingTasks(getCurrentUserId(), size));
    }

    @GetMapping("/notifications")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<List<TeacherNotificationVO>> getNotifications(@RequestParam(defaultValue = "5") int size) {
        return R.ok(teacherService.getNotifications(getCurrentUserId(), size));
    }

    @GetMapping("/courses")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<PageResult<TeacherCourseVO>> getMyCourses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size) {
        return R.ok(teacherService.getMyCourses(getCurrentUserId(), page, size));
    }

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }
        throw new BusinessException(ErrorCode.TOKEN_INVALID, "无法识别当前用户");
    }
}
