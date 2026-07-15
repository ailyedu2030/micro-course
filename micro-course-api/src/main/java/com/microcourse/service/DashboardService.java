package com.microcourse.service;

import com.microcourse.dto.DashboardActivityVO;
import com.microcourse.dto.DashboardOverviewVO;
import com.microcourse.dto.DashboardProgressVO;
import com.microcourse.dto.DashboardRevenueVO;

public interface DashboardService {

    DashboardOverviewVO getOverview();

    DashboardProgressVO getProgress();

    DashboardActivityVO getActivity();

    DashboardRevenueVO getRevenue();
}