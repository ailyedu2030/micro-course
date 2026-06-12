package com.microcourse.dto;

import java.time.LocalDateTime;

public class TeacherNotificationVO {

    private Long id;
    private String title;
    private String content;
    private LocalDateTime createdAt;

    public TeacherNotificationVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}