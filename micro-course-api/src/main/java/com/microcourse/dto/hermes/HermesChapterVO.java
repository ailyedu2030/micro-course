package com.microcourse.dto.hermes;

import java.time.LocalDateTime;

public class HermesChapterVO {

    private Long id;
    private String title;
    private String description;
    private Integer sortOrder;
    private Integer duration;
    private String learningObjectives;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public HermesChapterVO() {}

    public HermesChapterVO(Long id, String title, String description, Integer sortOrder,
                           Integer duration, String learningObjectives,
                           LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.sortOrder = sortOrder;
        this.duration = duration;
        this.learningObjectives = learningObjectives;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public String getLearningObjectives() { return learningObjectives; }
    public void setLearningObjectives(String learningObjectives) { this.learningObjectives = learningObjectives; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
