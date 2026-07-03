package com.microcourse.service;

import com.microcourse.dto.CourseTrendVO;
import com.microcourse.dto.DailyActivityVO;
import com.microcourse.dto.DashboardOverviewVO;
import com.microcourse.dto.UserTrendVO;

import java.util.List;

/**
 * 管理后台数据统计服务接口
 */
public interface AdminStatsService {

    /** 获取平台营收数据 */
    com.microcourse.dto.AdminRevenueVO getRevenueStats();

    /**
     * 获取数据看板概览
     */
    DashboardOverviewVO getOverview();

    /**
     * 获取用户趋势（按天聚合）
     * @param days 统计天数
     */
    List<UserTrendVO> getUserTrend(int days);

    /**
     * 获取课程趋势（按天聚合）
     * @param days 统计天数
     */
    List<CourseTrendVO> getCourseTrend(int days);

    /**
     * 获取课程状态分布统计
     * @return 各状态课程数量列表
     */
    List<java.util.Map<String, Object>> getCourseDistribution();

    /**
     * 获取学习行为统计
     * @return 各行为类型次数列表
     */
    List<java.util.Map<String, Object>> getLearningBehavior();

    /**
     * 获取每日活跃用户数
     * @param days 统计天数
     * @return 每日活跃用户数列表
     */
    List<DailyActivityVO> getDailyActivity(int days);

    /**
     * 获取系统健康状态
     * @return 健康状态Map（db/redis/disk/memory）
     */
    java.util.Map<String, String> getHealth();
}