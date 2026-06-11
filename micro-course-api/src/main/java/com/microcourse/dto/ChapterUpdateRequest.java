package com.microcourse.dto;

public class ChapterUpdateRequest {

    private String title;

    private String description;

    private Integer sortOrder;

    private String chapterType;

    private Integer duration;

    public ChapterUpdateRequest() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getChapterType() { return chapterType; }
    public void setChapterType(String chapterType) { this.chapterType = chapterType; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
}