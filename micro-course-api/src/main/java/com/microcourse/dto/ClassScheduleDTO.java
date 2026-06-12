package com.microcourse.dto;

import java.util.List;

public class ClassScheduleDTO {

    private Integer dayOfWeek;
    private Integer startPeriod;
    private Integer endPeriod;
    private String startTime;
    private String endTime;
    private String location;
    private String weekPattern;
    private String customWeeks;

    public ClassScheduleDTO() {}

    public Integer getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(Integer dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public Integer getStartPeriod() { return startPeriod; }
    public void setStartPeriod(Integer startPeriod) { this.startPeriod = startPeriod; }
    public Integer getEndPeriod() { return endPeriod; }
    public void setEndPeriod(Integer endPeriod) { this.endPeriod = endPeriod; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getWeekPattern() { return weekPattern; }
    public void setWeekPattern(String weekPattern) { this.weekPattern = weekPattern; }
    public String getCustomWeeks() { return customWeeks; }
    public void setCustomWeeks(String customWeeks) { this.customWeeks = customWeeks; }
}