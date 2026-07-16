package com.microcourse.plugin.interactive.dto;

import jakarta.validation.constraints.*;

public class CreateTrainingRequest {
    @NotNull(message = "实训序号不能为空")
    @Min(value = 1, message = "实训序号必须 ≥ 1")
    @Max(value = 20, message = "实训序号必须 ≤ 20")
    private Integer no;

    @Size(max = 100, message = "关联章节不能超过 100 字")
    private String chapter;

    @NotBlank(message = "实训标题不能为空")
    @Size(max = 200, message = "实训标题不能超过 200 字")
    private String title;

    @NotNull(message = "实训学时不能为空")
    @Min(value = 1, message = "实训学时必须 ≥ 1")
    @Max(value = 100, message = "实训学时必须 ≤ 100")
    private Integer hours;

    @Size(max = 200, message = "提交形式不能超过 200 字")
    private String submissionForm;

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
