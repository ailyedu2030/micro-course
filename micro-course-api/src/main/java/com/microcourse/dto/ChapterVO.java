package com.microcourse.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ChapterVO {

    private Long id;
    private Long courseId;
    private String title;
    private String description;
    private Integer sortOrder;
    private String chapterType;
    private Integer duration;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
    private String learningObjectives;
    private List<String> keyConcepts;
    private List<Object> exercises;

    public ChapterVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getChapterType() { return chapterType; }
    public void setChapterType(String chapterType) { this.chapterType = chapterType; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getLearningObjectives() { return learningObjectives; }
    public void setLearningObjectives(String learningObjectives) { this.learningObjectives = learningObjectives; }
    public List<String> getKeyConcepts() { return keyConcepts; }
    public void setKeyConcepts(List<String> keyConcepts) { this.keyConcepts = keyConcepts; }
    public List<Object> getExercises() { return exercises; }
    public void setExercises(List<Object> exercises) { this.exercises = exercises; }
}