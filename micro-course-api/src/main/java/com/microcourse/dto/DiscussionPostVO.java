package com.microcourse.dto;

import java.time.LocalDateTime;
import java.util.List;

public class DiscussionPostVO {

    private Long id;
    private Long courseId;
    private Long chapterId;
    private Long userId;
    private String authorName;
    private String title;
    private String content;
    private Boolean isAnonymous;
    private Boolean isPinned;
    private Boolean isEssence;
    private Integer commentCount;
    private Integer likeCount;
    private Boolean isOwner;
    private LocalDateTime createdAt;
    private List<DiscussionCommentVO> children;
    private String courseName;
    private String status;
    private String rejectReason;

    public DiscussionPostVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Boolean getIsAnonymous() { return isAnonymous; }
    public void setIsAnonymous(Boolean isAnonymous) { this.isAnonymous = isAnonymous; }
    public Boolean getIsPinned() { return isPinned; }
    public void setIsPinned(Boolean isPinned) { this.isPinned = isPinned; }
    public Boolean getIsEssence() { return isEssence; }
    public void setIsEssence(Boolean isEssence) { this.isEssence = isEssence; }
    public Integer getCommentCount() { return commentCount; }
    public void setCommentCount(Integer commentCount) { this.commentCount = commentCount; }
    public Integer getReplyCount() { return commentCount; }
    public void setReplyCount(Integer replyCount) { this.commentCount = replyCount; }
    public Integer getLikeCount() { return likeCount; }
    public void setLikeCount(Integer likeCount) { this.likeCount = likeCount; }
    public Boolean getIsOwner() { return isOwner; }
    public void setIsOwner(Boolean isOwner) { this.isOwner = isOwner; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public List<DiscussionCommentVO> getChildren() { return children; }
    public void setChildren(List<DiscussionCommentVO> children) { this.children = children; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
}