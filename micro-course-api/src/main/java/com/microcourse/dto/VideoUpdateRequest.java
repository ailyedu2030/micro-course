package com.microcourse.dto;

import jakarta.validation.constraints.Size;

public class VideoUpdateRequest {

    @Size(max = 200, message = "视频标题不能超过200字")
    private String title;

    private Integer sortOrder;

    private Integer status;

    private Long chapterId;

    public VideoUpdateRequest() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
}
