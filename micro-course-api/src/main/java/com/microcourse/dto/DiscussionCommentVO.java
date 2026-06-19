package com.microcourse.dto;

import java.time.LocalDateTime;
import java.util.List;

public class DiscussionCommentVO {

    private Long id;
    private Long postId;
    private Long parentId;
    private Long userId;
    private String authorName;
    private String content;
    private Boolean isAnonymous;
    private Boolean isTeacherReply;
    private String roleTag;
    private Boolean isOp;
    private Integer likeCount;
    private LocalDateTime createdAt;
    private List<DiscussionCommentVO> children;

    public DiscussionCommentVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Boolean getIsAnonymous() { return isAnonymous; }
    public void setIsAnonymous(Boolean isAnonymous) { this.isAnonymous = isAnonymous; }
    public Boolean getIsTeacherReply() { return isTeacherReply; }
    public void setIsTeacherReply(Boolean isTeacherReply) { this.isTeacherReply = isTeacherReply; }
    public String getRoleTag() { return roleTag; }
    public void setRoleTag(String roleTag) { this.roleTag = roleTag; }
    public Boolean getIsOp() { return isOp; }
    public void setIsOp(Boolean isOp) { this.isOp = isOp; }
    public Integer getLikeCount() { return likeCount; }
    public void setLikeCount(Integer likeCount) { this.likeCount = likeCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public List<DiscussionCommentVO> getChildren() { return children; }
    public void setChildren(List<DiscussionCommentVO> children) { this.children = children; }
}