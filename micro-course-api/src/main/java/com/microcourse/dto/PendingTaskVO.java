package com.microcourse.dto;

import java.time.LocalDateTime;

public class PendingTaskVO {

    private Long id;
    private String type;
    private String title;
    private LocalDateTime createdAt;

    public PendingTaskVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}