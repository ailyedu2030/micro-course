package com.microcourse.dto;

/**
 * 每日活跃用户 VO
 */
public class DailyActivityVO {

    private String date;
    private Long activeUsers;

    public DailyActivityVO() {}

    public DailyActivityVO(String date, Long activeUsers) {
        this.date = date;
        this.activeUsers = activeUsers;
    }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public Long getActiveUsers() { return activeUsers; }
    public void setActiveUsers(Long activeUsers) { this.activeUsers = activeUsers; }
}
