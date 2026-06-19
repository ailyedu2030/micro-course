package com.microcourse.dto;

import java.time.LocalDateTime;

public class CourseFavoriteVO {

    private Long id;
    private Long userId;
    private Long courseId;
    private String courseTitle;
    /** 课程封面 URL（P0-2: 支持前端"已收藏"Tab 直接展示未选课的收藏课程封面） */
    private String coverUrl;
    /** 教师姓名（P0-2: 支持前端"已收藏"Tab 直接展示未选课的收藏课程教师） */
    private String teacherName;
    private LocalDateTime createdAt;

    public CourseFavoriteVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getCourseTitle() { return courseTitle; }
    public void setCourseTitle(String courseTitle) { this.courseTitle = courseTitle; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public String getTeacherName() { return teacherName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
