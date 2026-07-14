package com.microcourse.controller;

import com.microcourse.dto.DashboardActivityVO;
import com.microcourse.dto.DashboardOverviewVO;
import com.microcourse.dto.DashboardProgressVO;
import com.microcourse.dto.DashboardRevenueVO;
import com.microcourse.dto.R;
import com.microcourse.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC','TEACHER')")
    public R<DashboardOverviewVO> getOverview() {
        return R.ok(dashboardService.getOverview());
    }

    @GetMapping("/progress")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC','TEACHER')")
    public R<DashboardProgressVO> getProgress() {
        return R.ok(dashboardService.getProgress());
    }

    @GetMapping("/activity")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC','TEACHER')")
    public R<DashboardActivityVO> getActivity() {
        return R.ok(dashboardService.getActivity());
    }

    @GetMapping("/revenue")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<DashboardRevenueVO> getRevenue() {
        return R.ok(dashboardService.getRevenue());
    }
}