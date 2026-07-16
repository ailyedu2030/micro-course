package com.microcourse.plugin.interactive.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("course_final_project")
public class CourseFinalProject {
    @TableId(type = IdType.AUTO) private Long id;
    @TableField("course_id") private Long courseId;
    private String title;
    @TableField("phases") private String phases;
    @TableField("final_submission_form") private String finalSubmissionForm;
    @TableField("created_at") private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getPhases() { return phases; }
    public void setPhases(String phases) { this.phases = phases; }
    public String getFinalSubmissionForm() { return finalSubmissionForm; }
    public void setFinalSubmissionForm(String submissionForm) { this.finalSubmissionForm = submissionForm; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
