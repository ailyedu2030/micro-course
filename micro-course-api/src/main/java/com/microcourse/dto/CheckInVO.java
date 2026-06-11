package com.microcourse.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class CheckInVO {

    private Long id;
    private Long userId;
    private LocalDate checkinDate;
    private Integer duration;
    private Integer streakDays;
    private LocalDateTime createdAt;

    public CheckInVO() {}

    public CheckInVO(Long id, Long userId, LocalDate checkinDate, Integer duration,
                      Integer streakDays, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.checkinDate = checkinDate;
        this.duration = duration;
        this.streakDays = streakDays;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LocalDate getCheckinDate() { return checkinDate; }
    public void setCheckinDate(LocalDate checkinDate) { this.checkinDate = checkinDate; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public Integer getStreakDays() { return streakDays; }
    public void setStreakDays(Integer streakDays) { this.streakDays = streakDays; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}