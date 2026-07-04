package com.microcourse.dto.lesson;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class LessonCreateRequest {

    @NotNull(message = "章节ID不能为空")
    private Long chapterId;

    @NotNull(message = "课程ID不能为空")
    private Long courseId;

    @NotBlank(message = "课时标题不能为空")
    private String title;

    private String lessonType;

    public LessonCreateRequest() {}

    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getLessonType() { return lessonType; }
    public void setLessonType(String lessonType) { this.lessonType = lessonType; }
}
