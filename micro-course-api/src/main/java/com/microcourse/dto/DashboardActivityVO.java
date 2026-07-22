package com.microcourse.dto;

import java.util.List;
import java.util.Map;

public class DashboardActivityVO {

    private Long dailyActiveUsers;
    private Long weeklyActiveUsers;
    private Long monthlyActiveUsers;
    private Long totalLogins;
    private List<Map<String, Object>> dailyTrend;
    private List<Map<String, Object>> topActiveStudents;

    public DashboardActivityVO() {}

    public Long getDailyActiveUsers() { return dailyActiveUsers; }
    public void setDailyActiveUsers(Long dailyActiveUsers) { this.dailyActiveUsers = dailyActiveUsers; }
    public Long getWeeklyActiveUsers() { return weeklyActiveUsers; }
    public void setWeeklyActiveUsers(Long weeklyActiveUsers) { this.weeklyActiveUsers = weeklyActiveUsers; }
    public Long getMonthlyActiveUsers() { return monthlyActiveUsers; }
    public void setMonthlyActiveUsers(Long monthlyActiveUsers) { this.monthlyActiveUsers = monthlyActiveUsers; }
    public Long getTotalLogins() { return totalLogins; }
    public void setTotalLogins(Long totalLogins) { this.totalLogins = totalLogins; }
    public List<Map<String, Object>> getDailyTrend() { return dailyTrend; }
    public void setDailyTrend(List<Map<String, Object>> dailyTrend) { this.dailyTrend = dailyTrend; }
    public List<Map<String, Object>> getTopActiveStudents() { return topActiveStudents; }
    public void setTopActiveStudents(List<Map<String, Object>> topActiveStudents) { this.topActiveStudents = topActiveStudents; }
}
