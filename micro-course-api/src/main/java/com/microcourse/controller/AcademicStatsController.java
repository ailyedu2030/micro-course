package com.microcourse.controller;

import com.microcourse.dto.AcademicOverviewVO;
import com.microcourse.dto.CompletionWarningVO;
import com.microcourse.dto.DepartmentDetailVO;
import com.microcourse.dto.DepartmentStatsVO;
import com.microcourse.dto.R;
import com.microcourse.dto.TrendPointVO;
import com.microcourse.service.AcademicStatsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 教务处驾驶舱统计接口
 * 面向 ACADEMIC 角色的跨院系统计 API
 */
@RestController
@RequestMapping("/api/academic/stats")
@PreAuthorize("hasAnyRole('ACADEMIC', 'ADMIN')")
public class AcademicStatsController {

    private final AcademicStatsService academicStatsService;

    public AcademicStatsController(AcademicStatsService academicStatsService) {
        this.academicStatsService = academicStatsService;
    }

    /**
     * GET /api/academic/stats/overview
     * 全校概览数据
     */
    @GetMapping("/overview")
    public R<AcademicOverviewVO> getOverview() {
        return R.ok(academicStatsService.getOverview());
    }

    /**
     * GET /api/academic/stats/departments
     * 院系列表（含统计）
     */
    @GetMapping("/departments")
    public R<List<DepartmentStatsVO>> getDepartmentStats() {
        return R.ok(academicStatsService.getDepartmentStats());
    }

    /**
     * GET /api/academic/stats/department/{id}
     * 单院详情
     */
    @GetMapping("/department/{id}")
    public R<DepartmentDetailVO> getDepartmentDetail(@PathVariable("id") Long departmentId) {
        return R.ok(academicStatsService.getDepartmentDetail(departmentId));
    }

    /**
     * GET /api/academic/stats/warnings
     * 完成率预警课程（完成率 < 30%）
     */
    @GetMapping("/warnings")
    public R<List<CompletionWarningVO>> getCompletionWarnings() {
        return R.ok(academicStatsService.getCompletionWarnings());
    }

    /**
     * GET /api/academic/stats/participation-trend?semester=2025-1
     * 参与率趋势
     */
    @GetMapping("/participation-trend")
    public R<List<TrendPointVO>> getParticipationTrend(
            @RequestParam(required = false) String semester) {
        return R.ok(academicStatsService.getParticipationTrend(semester));
    }

    /**
     * GET /api/academic/stats/completion-trend?semester=2025-1
     * 完成率趋势
     */
    @GetMapping("/completion-trend")
    public R<List<TrendPointVO>> getCompletionTrend(
            @RequestParam(required = false) String semester) {
        return R.ok(academicStatsService.getCompletionTrend(semester));
    }
}
