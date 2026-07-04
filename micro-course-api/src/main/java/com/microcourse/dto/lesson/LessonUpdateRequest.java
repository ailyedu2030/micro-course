package com.microcourse.dto.lesson;

public class LessonUpdateRequest {

    private String title;
    private Integer duration;
    private Boolean visible;

    public LessonUpdateRequest() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public Boolean getVisible() { return visible; }
    public void setVisible(Boolean visible) { this.visible = visible; }
}
