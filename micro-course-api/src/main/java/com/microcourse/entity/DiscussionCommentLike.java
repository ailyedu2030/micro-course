package com.microcourse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 讨论评论点赞关系表。
 * <p>由 {@link com.microcourse.service.impl.DiscussionCommentServiceImpl#like} 使用，
 * 替代原有的 JdbcTemplate 手动 SQL 操作。</p>
 */
@TableName("discussion_comment_likes")
public class DiscussionCommentLike {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("comment_id")
    private Long commentId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public DiscussionCommentLike() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getCommentId() { return commentId; }
    public void setCommentId(Long commentId) { this.commentId = commentId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
