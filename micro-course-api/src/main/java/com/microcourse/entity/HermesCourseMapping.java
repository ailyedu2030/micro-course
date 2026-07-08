package com.microcourse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("hermes_course_mapping")
public class HermesCourseMapping {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String hermesCourseId;

    private Long courseId;

    private String hermesTeacherId;

    private LocalDateTime lastSyncAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public HermesCourseMapping() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getHermesCourseId() { return hermesCourseId; }
    public void setHermesCourseId(String hermesCourseId) { this.hermesCourseId = hermesCourseId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getHermesTeacherId() { return hermesTeacherId; }
    public void setHermesTeacherId(String hermesTeacherId) { this.hermesTeacherId = hermesTeacherId; }
    public LocalDateTime getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(LocalDateTime lastSyncAt) { this.lastSyncAt = lastSyncAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
