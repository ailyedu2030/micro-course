package com.microcourse.dto;

import jakarta.validation.constraints.Size;

public class ChapterUpdateRequest {

    @Size(max = 200, message = "章节标题不能超过200字")
    private String title;

    private String description;

    private Integer sortOrder;

    private Integer duration;

    // ===== P1 Stage 1 =====
    private Integer no;
    private String anchorPoint;
    private String coreQuestion;
    private Integer chapterHours;

    public ChapterUpdateRequest() {}

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
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
}