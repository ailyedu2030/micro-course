package com.microcourse.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public class OfflineSessionCreateRequest {

    @NotNull(message = "上课日期不能为空")
    private LocalDate sessionDate;

    @NotNull(message = "开始时间不能为空")
    private LocalTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalTime endTime;

    @NotNull(message = "上课地点不能为空")
    @Size(max = 200, message = "上课地点不能超过200字")
    private String location;

    private String teacherNotes;

    @NotNull(message = "排序不能为空")
    private Integer sortOrder;

    public OfflineSessionCreateRequest() {}

    public LocalDate getSessionDate() { return sessionDate; }
    public void setSessionDate(LocalDate sessionDate) { this.sessionDate = sessionDate; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getTeacherNotes() { return teacherNotes; }
    public void setTeacherNotes(String teacherNotes) { this.teacherNotes = teacherNotes; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
