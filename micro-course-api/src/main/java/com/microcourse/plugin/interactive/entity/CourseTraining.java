package com.microcourse.plugin.interactive.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("course_trainings")
public class CourseTraining {
    @TableId(type = IdType.AUTO) private Long id;
    @TableField("course_id") private Long courseId;
    private Integer no;
    private String chapter;
    private String title;
    private Integer hours;
    @TableField("submission_form") private String submissionForm;
    @TableField("created_at") private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
