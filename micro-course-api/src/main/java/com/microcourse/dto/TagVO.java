package com.microcourse.dto;

import java.time.LocalDateTime;

public class TagVO {

    private Long id;
    private String name;
    private String color;
    private LocalDateTime createdAt;

    public TagVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}