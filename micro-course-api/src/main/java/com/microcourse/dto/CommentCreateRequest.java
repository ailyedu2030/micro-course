package com.microcourse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CommentCreateRequest {

    @NotNull(message = "帖子ID不能为空")
    private Long postId;

    private Long parentId;

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 2000, message = "评论最多2000字符")
    private String content;

    public CommentCreateRequest() {}

    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}