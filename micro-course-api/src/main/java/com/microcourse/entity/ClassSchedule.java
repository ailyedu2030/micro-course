package com.microcourse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("class_schedules")
public class ClassSchedule {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("class_id")
    private Long classId;

    @TableField("day_of_week")
    private Integer dayOfWeek;

    @TableField("start_period")
    private Integer startPeriod;

    @TableField("end_period")
    private Integer endPeriod;

    @TableField("start_time")
    private String startTime;

    @TableField("end_time")
    private String endTime;

    @TableField("location")
    private String location;

    @TableField("week_pattern")
    private String weekPattern;

    @TableField("custom_weeks")
    private String customWeeks;

    public ClassSchedule() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getClassId() {
        return classId;
    }

    public void setClassId(Long classId) {
        this.classId = classId;
    }

    public Integer getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(Integer dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public Integer getStartPeriod() {
        return startPeriod;
    }

    public void setStartPeriod(Integer startPeriod) {
        this.startPeriod = startPeriod;
    }

    public Integer getEndPeriod() {
        return endPeriod;
    }

    public void setEndPeriod(Integer endPeriod) {
        this.endPeriod = endPeriod;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getWeekPattern() {
        return weekPattern;
    }

    public void setWeekPattern(String weekPattern) {
        this.weekPattern = weekPattern;
    }

    public String getCustomWeeks() {
        return customWeeks;
    }

    public void setCustomWeeks(String customWeeks) {
        this.customWeeks = customWeeks;
    }
}