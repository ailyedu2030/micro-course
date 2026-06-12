package com.microcourse.controller;

import com.microcourse.dto.*;
import com.microcourse.service.TeacherService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<TeacherStatsVO> getStats(@AuthenticationPrincipal UserDetails userDetails) {
        Long teacherId = getCurrentUserId(userDetails);
        return R.ok(teacherService.getStats(teacherId));
    }

    @GetMapping("/student-activity")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<List<StudentActivityVO>> getStudentActivity(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "7") int days) {
        Long teacherId = getCurrentUserId(userDetails);
        return R.ok(teacherService.getStudentActivity(teacherId, days));
    }

    @GetMapping("/pending-tasks")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<List<PendingTaskVO>> getPendingTasks(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "5") int size) {
        Long teacherId = getCurrentUserId(userDetails);
        return R.ok(teacherService.getPendingTasks(teacherId, size));
    }

    @GetMapping("/notifications")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<List<TeacherNotificationVO>> getNotifications(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "5") int size) {
        Long teacherId = getCurrentUserId(userDetails);
        return R.ok(teacherService.getNotifications(teacherId, size));
    }

    @GetMapping("/courses")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<PageResult<TeacherCourseVO>> getMyCourses(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size) {
        Long teacherId = getCurrentUserId(userDetails);
        return R.ok(teacherService.getMyCourses(teacherId, page, size));
    }

    private Long getCurrentUserId(UserDetails userDetails) {
        // 从 UserDetails 获取用户 ID（通过自定义 UserDetails 实现）
        if (userDetails instanceof com.microcourse.entity.User) {
            return ((com.microcourse.entity.User) userDetails).getId();
        }
        // 降级处理：从 username 解析（假设 username 是数字 ID）
        try {
            return Long.parseLong(userDetails.getUsername());
        } catch (NumberFormatException e) {
            return 1L; // 降级默认值
        }
    }
}