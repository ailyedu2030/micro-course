package com.microcourse.dto;

/**
 * 用户趋势 VO（按天聚合）
 */
public class UserTrendVO {

    private String date;
    private Long newUsers;
    private Long activeUsers;

    public UserTrendVO() {}

    public UserTrendVO(String date, Long newUsers, Long activeUsers) {
        this.date = date;
        this.newUsers = newUsers;
        this.activeUsers = activeUsers;
    }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public Long getNewUsers() { return newUsers; }
    public void setNewUsers(Long newUsers) { this.newUsers = newUsers; }
    public Long getActiveUsers() { return activeUsers; }
    public void setActiveUsers(Long activeUsers) { this.activeUsers = activeUsers; }
}