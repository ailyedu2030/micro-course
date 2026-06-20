package com.microcourse.dto.lesson;

import java.time.LocalDateTime;

public class LessonVO {
    private Long id;
    private Long chapterId;
    private Long courseId;
    private String title;
    private String lessonType;
    private Integer sortOrder;
    private Integer duration;
    private Boolean visible;
    private String videoUrl;
    private Integer slideCount;
    private LocalDateTime createdAt;

    public LessonVO() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getLessonType() { return lessonType; }
    public void setLessonType(String lessonType) { this.lessonType = lessonType; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public Boolean getVisible() { return visible; }
    public void setVisible(Boolean visible) { this.visible = visible; }
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    public Integer getSlideCount() { return slideCount; }
    public void setSlideCount(Integer slideCount) { this.slideCount = slideCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public static class SortItem {
        private Long id;
        private Long chapterId;
        private Integer sortOrder;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getChapterId() { return chapterId; }
        public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    }
}
