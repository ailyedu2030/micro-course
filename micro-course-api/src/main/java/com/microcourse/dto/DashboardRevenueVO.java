package com.microcourse.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class DashboardRevenueVO {

    private BigDecimal totalRevenue;
    private BigDecimal monthlyRevenue;
    private Long totalOrders;
    private Long paidOrders;
    private BigDecimal avgOrderAmount;
    private List<Map<String, Object>> dailyTrend;
    private List<Map<String, Object>> topCourses;

    public DashboardRevenueVO() {}

    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }
    public BigDecimal getMonthlyRevenue() { return monthlyRevenue; }
    public void setMonthlyRevenue(BigDecimal monthlyRevenue) { this.monthlyRevenue = monthlyRevenue; }
    public Long getTotalOrders() { return totalOrders; }
    public void setTotalOrders(Long totalOrders) { this.totalOrders = totalOrders; }
    public Long getPaidOrders() { return paidOrders; }
    public void setPaidOrders(Long paidOrders) { this.paidOrders = paidOrders; }
    public BigDecimal getAvgOrderAmount() { return avgOrderAmount; }
    public void setAvgOrderAmount(BigDecimal avgOrderAmount) { this.avgOrderAmount = avgOrderAmount; }
    public List<Map<String, Object>> getDailyTrend() { return dailyTrend; }
    public void setDailyTrend(List<Map<String, Object>> dailyTrend) { this.dailyTrend = dailyTrend; }
    public List<Map<String, Object>> getTopCourses() { return topCourses; }
    public void setTopCourses(List<Map<String, Object>> topCourses) { this.topCourses = topCourses; }
}