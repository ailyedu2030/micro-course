package com.microcourse.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 管理端营收数据 VO
 */
public class AdminRevenueVO {

    private BigDecimal totalRevenue;         // 平台总收入
    private BigDecimal platformShareTotal;   // 平台实际分成收入
    private BigDecimal teacherPayoutTotal;   // 教师待结算总额
    private int totalOrders;                 // 付费订单总数
    private int paidStudentCount;            // 付费学员数
    private int teacherCount;                // 有收入的教师数
    private List<MonthlyRevenueItem> monthlyTrend;
    private List<TopTeacherItem> topTeachers;

    public AdminRevenueVO() {}

    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }
    public BigDecimal getPlatformShareTotal() { return platformShareTotal; }
    public void setPlatformShareTotal(BigDecimal platformShareTotal) { this.platformShareTotal = platformShareTotal; }
    public BigDecimal getTeacherPayoutTotal() { return teacherPayoutTotal; }
    public void setTeacherPayoutTotal(BigDecimal teacherPayoutTotal) { this.teacherPayoutTotal = teacherPayoutTotal; }
    public int getTotalOrders() { return totalOrders; }
    public void setTotalOrders(int totalOrders) { this.totalOrders = totalOrders; }
    public int getPaidStudentCount() { return paidStudentCount; }
    public void setPaidStudentCount(int paidStudentCount) { this.paidStudentCount = paidStudentCount; }
    public int getTeacherCount() { return teacherCount; }
    public void setTeacherCount(int teacherCount) { this.teacherCount = teacherCount; }
    public List<MonthlyRevenueItem> getMonthlyTrend() { return monthlyTrend; }
    public void setMonthlyTrend(List<MonthlyRevenueItem> monthlyTrend) { this.monthlyTrend = monthlyTrend; }
    public List<TopTeacherItem> getTopTeachers() { return topTeachers; }
    public void setTopTeachers(List<TopTeacherItem> topTeachers) { this.topTeachers = topTeachers; }

    public static class MonthlyRevenueItem {
        private String month;            // "2026-01"
        private BigDecimal revenue;
        private int orderCount;

        public String getMonth() { return month; }
        public void setMonth(String month) { this.month = month; }
        public BigDecimal getRevenue() { return revenue; }
        public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }
        public int getOrderCount() { return orderCount; }
        public void setOrderCount(int orderCount) { this.orderCount = orderCount; }
    }

    public static class TopTeacherItem {
        private Long teacherId;
        private String teacherName;
        private BigDecimal revenue;
        private int orderCount;
        private String tier;

        public Long getTeacherId() { return teacherId; }
        public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
        public String getTeacherName() { return teacherName; }
        public void setTeacherName(String teacherName) { this.teacherName = teacherName; }
        public BigDecimal getRevenue() { return revenue; }
        public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }
        public int getOrderCount() { return orderCount; }
        public void setOrderCount(int orderCount) { this.orderCount = orderCount; }
        public String getTier() { return tier; }
        public void setTier(String tier) { this.tier = tier; }
    }
}
