package com.microcourse.dto;

import jakarta.validation.constraints.Size;

public class PostUpdateRequest {

    @Size(max = 200, message = "标题最多200字符")
    private String title;

    private String content;

    private Boolean isAnonymous;

    public PostUpdateRequest() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Boolean getIsAnonymous() { return isAnonymous; }
    public void setIsAnonymous(Boolean isAnonymous) { this.isAnonymous = isAnonymous; }
}