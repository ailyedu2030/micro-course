package com.microcourse.plugin.interactive.dto;

import jakarta.validation.constraints.*;

public class CreateTrainingRequest {
    @NotNull @Min(1) @Max(20) private Integer no;
    @Size(max = 100) private String chapter;
    @NotBlank @Size(max = 200) private String title;
    @NotNull @Min(1) @Max(100) private Integer hours;
    @Size(max = 200) private String submissionForm;

    public Integer getNo() { return no; }
    public void setNo(Integer no) { this.no = no; }
    public String getChapter() { return chapter; }
    public void setChapter(String chapter) { this.chapter = chapter; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getHours() { return hours; }
    public void setHours(Integer hours) { this.hours = hours; }
    public String getSubmissionForm() { return submissionForm; }
    public void setSubmissionForm(String submissionForm) { this.submissionForm = submissionForm; }
}
