package com.microcourse.plugin.interactive.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public class CreateFinalProjectRequest {
    @NotBlank(message = "期末项目标题不能为空")
    @Size(max = 200, message = "标题不能超过 200 字")
    private String title;

    private @Valid List<@Size(max = 100, message = "阶段名称不能超过 100 字") String> phases;

    @Size(max = 200, message = "提交形式不能超过 200 字")
    private String finalSubmissionForm;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public List<String> getPhases() { return phases; }
    public void setPhases(List<String> phases) { this.phases = phases; }
    public String getFinalSubmissionForm() { return finalSubmissionForm; }
    public void setFinalSubmissionForm(String submissionForm) { this.finalSubmissionForm = submissionForm; }
}
