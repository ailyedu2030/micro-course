package com.microcourse.plugin.interactive.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public class CreateFinalProjectRequest {
    @NotBlank @Size(max = 200) private String title;
    private List<String> phases;  // JSON 数组,Jackson 直接反序列化
    @Size(max = 200) private String finalSubmissionForm;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public List<String> getPhases() { return phases; }
    public void setPhases(List<String> phases) { this.phases = phases; }
    public String getFinalSubmissionForm() { return finalSubmissionForm; }
    public void setFinalSubmissionForm(String submissionForm) { this.finalSubmissionForm = submissionForm; }
}
