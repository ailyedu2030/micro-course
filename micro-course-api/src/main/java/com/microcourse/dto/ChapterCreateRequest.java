package com.microcourse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ChapterCreateRequest {

    @NotBlank(message = "章节标题不能为空")
    private String title;

    @NotNull(message = "课程ID不能为空")
    private Long courseId;

    private String description;

    @NotNull(message = "排序不能为空")
    private Integer sortOrder;

    @NotBlank(message = "章节类型不能为空")
    private String chapterType = "VIDEO";

    private Integer duration;

    public ChapterCreateRequest() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getChapterType() { return chapterType; }
    public void setChapterType(String chapterType) { this.chapterType = chapterType; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
}