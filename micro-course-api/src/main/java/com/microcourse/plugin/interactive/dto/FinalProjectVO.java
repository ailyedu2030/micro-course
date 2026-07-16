package com.microcourse.plugin.interactive.dto;

import java.time.LocalDateTime;
import java.util.List;

public class FinalProjectVO {
    private Long id;
    private Long courseId;
    private String title;
    private List<String> phases;
    private String finalSubmissionForm;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public List<String> getPhases() { return phases; }
    public void setPhases(List<String> phases) { this.phases = phases; }
    public String getFinalSubmissionForm() { return finalSubmissionForm; }
    public void setFinalSubmissionForm(String submissionForm) { this.finalSubmissionForm = submissionForm; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
