package com.microcourse.controller;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.PendingTaskVO;
import com.microcourse.dto.R;
import com.microcourse.dto.StudentActivityVO;
import com.microcourse.dto.TeacherCourseVO;
import com.microcourse.dto.TeacherNotificationVO;
import com.microcourse.dto.TeacherRevenueVO;
import com.microcourse.dto.TeacherStatsVO;
import com.microcourse.service.TeacherService;
import com.microcourse.util.SecurityUtil;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teachers")
@Validated
public class TeacherController {

    private final TeacherService teacherService;

    public TeacherController(TeacherService teacherService) {
        this.teacherService = teacherService;
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<TeacherStatsVO> getStats() {
        return R.ok(teacherService.getStats(SecurityUtil.getCurrentUserId()));
    }

    @GetMapping("/student-activity")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<List<StudentActivityVO>> getStudentActivity(@RequestParam(defaultValue = "7") int days) {
        return R.ok(teacherService.getStudentActivity(SecurityUtil.getCurrentUserId(), days));
    }

    @GetMapping("/pending-tasks")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<List<PendingTaskVO>> getPendingTasks(@RequestParam(defaultValue = "5") @Range(min = 1, max = 100) int size) {
        return R.ok(teacherService.getPendingTasks(SecurityUtil.getCurrentUserId(), size));
    }

    @GetMapping("/notifications")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<List<TeacherNotificationVO>> getNotifications(@RequestParam(defaultValue = "5") @Range(min = 1, max = 100) int size) {
        return R.ok(teacherService.getNotifications(SecurityUtil.getCurrentUserId(), size));
    }

    @GetMapping("/revenue")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<TeacherRevenueVO> getRevenue() {
        return R.ok(teacherService.getRevenue(SecurityUtil.getCurrentUserId()));
    }

    @GetMapping("/courses")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<PageResult<TeacherCourseVO>> getMyCourses(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "8") @Range(min = 1, max = 100) int size) {
        return R.ok(teacherService.getMyCourses(SecurityUtil.getCurrentUserId(), page, size));
    }
}
