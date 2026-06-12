package com.microcourse.dto;

public class StudentActivityVO {

    private String date;
    private Integer studyMinutes;
    private Integer completionRate;
    private Integer activeUsers;

    public StudentActivityVO() {}

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public Integer getStudyMinutes() { return studyMinutes; }
    public void setStudyMinutes(Integer studyMinutes) { this.studyMinutes = studyMinutes; }
    public Integer getCompletionRate() { return completionRate; }
    public void setCompletionRate(Integer completionRate) { this.completionRate = completionRate; }
    public Integer getActiveUsers() { return activeUsers; }
    public void setActiveUsers(Integer activeUsers) { this.activeUsers = activeUsers; }
}