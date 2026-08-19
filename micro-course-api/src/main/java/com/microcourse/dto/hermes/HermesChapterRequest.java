package com.microcourse.dto.hermes;

import jakarta.validation.constraints.NotBlank;

public class HermesChapterRequest {

    @NotBlank(message = "title 不能为空")
    private String title;

    private Integer sortOrder;

    private String description;

    private Integer duration;

    private String learningObjectives;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public String getLearningObjectives() { return learningObjectives; }
    public void setLearningObjectives(String learningObjectives) { this.learningObjectives = learningObjectives; }
}
