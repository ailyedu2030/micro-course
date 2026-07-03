package com.microcourse.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 教师收益看板 VO
 */
public class TeacherRevenueVO {

    private BigDecimal totalRevenue;        // 总收入
    private BigDecimal platformShare;       // 平台分成
    private BigDecimal netEarnings;         // 教师净收入
    private int orderCount;                 // 付费订单数
    private int studentCount;               // 付费学员数
    private List<CourseRevenueItem> courseBreakdown;
    private List<RecentTransaction> recentTransactions;

    public TeacherRevenueVO() {}

    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }

    public BigDecimal getPlatformShare() { return platformShare; }
    public void setPlatformShare(BigDecimal platformShare) { this.platformShare = platformShare; }

    public BigDecimal getNetEarnings() { return netEarnings; }
    public void setNetEarnings(BigDecimal netEarnings) { this.netEarnings = netEarnings; }

    public int getOrderCount() { return orderCount; }
    public void setOrderCount(int orderCount) { this.orderCount = orderCount; }

    public int getStudentCount() { return studentCount; }
    public void setStudentCount(int studentCount) { this.studentCount = studentCount; }

    public List<CourseRevenueItem> getCourseBreakdown() { return courseBreakdown; }
    public void setCourseBreakdown(List<CourseRevenueItem> courseBreakdown) { this.courseBreakdown = courseBreakdown; }

    public List<RecentTransaction> getRecentTransactions() { return recentTransactions; }
    public void setRecentTransactions(List<RecentTransaction> recentTransactions) { this.recentTransactions = recentTransactions; }

    // ====== 内部类 ======

    public static class CourseRevenueItem {
        private Long courseId;
        private String courseTitle;
        private BigDecimal revenue;
        private int orderCount;
        private BigDecimal platformShare;
        private BigDecimal netEarnings;

        public Long getCourseId() { return courseId; }
        public void setCourseId(Long courseId) { this.courseId = courseId; }
        public String getCourseTitle() { return courseTitle; }
        public void setCourseTitle(String courseTitle) { this.courseTitle = courseTitle; }
        public BigDecimal getRevenue() { return revenue; }
        public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }
        public int getOrderCount() { return orderCount; }
        public void setOrderCount(int orderCount) { this.orderCount = orderCount; }
        public BigDecimal getPlatformShare() { return platformShare; }
        public void setPlatformShare(BigDecimal platformShare) { this.platformShare = platformShare; }
        public BigDecimal getNetEarnings() { return netEarnings; }
        public void setNetEarnings(BigDecimal netEarnings) { this.netEarnings = netEarnings; }
    }

    public static class RecentTransaction {
        private String orderNo;
        private String courseTitle;
        private BigDecimal amount;
        private BigDecimal platformShare;
        private BigDecimal netEarnings;
        private LocalDateTime paidAt;

        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public String getCourseTitle() { return courseTitle; }
        public void setCourseTitle(String courseTitle) { this.courseTitle = courseTitle; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public BigDecimal getPlatformShare() { return platformShare; }
        public void setPlatformShare(BigDecimal platformShare) { this.platformShare = platformShare; }
        public BigDecimal getNetEarnings() { return netEarnings; }
        public void setNetEarnings(BigDecimal netEarnings) { this.netEarnings = netEarnings; }
        public LocalDateTime getPaidAt() { return paidAt; }
        public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
    }
}
