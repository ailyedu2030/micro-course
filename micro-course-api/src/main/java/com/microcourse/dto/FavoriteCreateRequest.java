package com.microcourse.dto;

public class FavoriteCreateRequest {

    private Long courseId;

    public FavoriteCreateRequest() {}

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
}
