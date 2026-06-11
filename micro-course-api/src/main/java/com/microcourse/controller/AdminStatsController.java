package com.microcourse.controller;

import com.microcourse.dto.CourseTrendVO;
import com.microcourse.dto.DashboardOverviewVO;
import com.microcourse.dto.R;
import com.microcourse.dto.UserTrendVO;
import com.microcourse.service.AdminStatsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理后台数据看板统计接口
 */
@RestController
@RequestMapping("/api/admin/stats")
@PreAuthorize("hasAnyRole('ADMIN')")
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    public AdminStatsController(AdminStatsService adminStatsService) {
        this.adminStatsService = adminStatsService;
    }

    /**
     * GET /api/admin/stats/overview
     * 返回数据看板概览
     */
    @GetMapping("/overview")
    public R<DashboardOverviewVO> getOverview() {
        DashboardOverviewVO vo = adminStatsService.getOverview();
        return R.ok(vo);
    }

    /**
     * GET /api/admin/stats/users?days=7
     * 返回用户趋势（按天聚合）
     */
    @GetMapping("/users")
    public R<List<UserTrendVO>> getUserTrend(@RequestParam(defaultValue = "7") int days) {
        if (days <= 0) {
            days = 7;
        }
        List<UserTrendVO> list = adminStatsService.getUserTrend(days);
        return R.ok(list);
    }

    /**
     * GET /api/admin/stats/courses?days=7
     * 返回课程趋势（按天聚合）
     */
    @GetMapping("/courses")
    public R<List<CourseTrendVO>> getCourseTrend(@RequestParam(defaultValue = "7") int days) {
        if (days <= 0) {
            days = 7;
        }
        List<CourseTrendVO> list = adminStatsService.getCourseTrend(days);
        return R.ok(list);
    }

    /**
     * GET /api/admin/stats/course-distribution
     * 返回课程状态分布统计
     */
    @GetMapping("/course-distribution")
    public R<List<Map<String, Object>>> getCourseDistribution() {
        List<Map<String, Object>> result = adminStatsService.getCourseDistribution();
        return R.ok(result);
    }

    /**
     * GET /api/admin/stats/learning-behavior
     * 返回学习行为统计
     */
    @GetMapping("/learning-behavior")
    public R<List<Map<String, Object>>> getLearningBehavior() {
        List<Map<String, Object>> result = adminStatsService.getLearningBehavior();
        return R.ok(result);
    }
}