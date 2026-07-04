package com.microcourse.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 课程评价视图对象
 *
 * @author Phase9-Development-Team
 * @since 2026-06-12
 */
public class CourseReviewVO {

    private Long id;
    private Long courseId;
    private String courseTitle;
    private Long userId;
    private String username;
    private String realName;
    private Integer rating;
    private String content;
    private Boolean isAnonymous;
    private Long parentId;
    private Integer status;
    private List<CourseReviewVO> replies;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public CourseReviewVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getCourseTitle() { return courseTitle; }
    public void setCourseTitle(String courseTitle) { this.courseTitle = courseTitle; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Boolean getIsAnonymous() { return isAnonymous; }
    public void setIsAnonymous(Boolean isAnonymous) { this.isAnonymous = isAnonymous; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public List<CourseReviewVO> getReplies() { return replies; }
    public void setReplies(List<CourseReviewVO> replies) { this.replies = replies; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}