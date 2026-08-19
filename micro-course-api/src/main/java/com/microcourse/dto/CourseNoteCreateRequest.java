package com.microcourse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CourseNoteCreateRequest {

    @NotNull(message = "课程ID不能为空")
    private Long courseId;

    private Long chapterId;

    private Long videoId;

    private Integer videoPosition;

    @Size(max = 200, message = "笔记标题不能超过200字")
    private String title;

    @NotBlank(message = "笔记内容不能为空")
    @Size(max = 2000, message = "笔记内容不能超过2000字")
    private String content;

    private Boolean isPublic;

    public CourseNoteCreateRequest() {}

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public Long getVideoId() { return videoId; }
    public void setVideoId(Long videoId) { this.videoId = videoId; }
    public Integer getVideoPosition() { return videoPosition; }
    public void setVideoPosition(Integer videoPosition) { this.videoPosition = videoPosition; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Boolean getIsPublic() { return isPublic; }
    public void setIsPublic(Boolean isPublic) { this.isPublic = isPublic; }
}
