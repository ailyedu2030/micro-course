package com.microcourse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ChapterCreateRequest {

    @NotBlank(message = "章节标题不能为空")
    private String title;

    // Stage 4: 移除了 @NotNull,因为 AliasController 从 path 注入 courseId。
    // @Valid 校验在方法体之前,path 参数设置 courseId 前已校验失败。
    // 服务层 validateCourseId() 兜底处理 null。
    private Long courseId;

    private String description;

    @NotNull(message = "排序不能为空")
    private Integer sortOrder;

    private Integer duration;

    // ===== P1 Stage 1: 章节元信息(Trae SKILL.md 模块 3.2 schema)=====
    private Integer no;
    private String anchorPoint;
    private String coreQuestion;
    private Integer chapterHours;

    public ChapterCreateRequest() {}

    public Integer getNo() { return no; }
    public void setNo(Integer no) { this.no = no; }
    public String getAnchorPoint() { return anchorPoint; }
    public void setAnchorPoint(String anchorPoint) { this.anchorPoint = anchorPoint; }
    public String getCoreQuestion() { return coreQuestion; }
    public void setCoreQuestion(String coreQuestion) { this.coreQuestion = coreQuestion; }
    public Integer getChapterHours() { return chapterHours; }
    public void setChapterHours(Integer chapterHours) { this.chapterHours = chapterHours; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
}