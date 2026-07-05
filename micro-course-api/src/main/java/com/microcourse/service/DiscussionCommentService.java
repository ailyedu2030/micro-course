package com.microcourse.service;

import com.microcourse.dto.CommentCreateRequest;
import com.microcourse.dto.DiscussionCommentVO;
import com.microcourse.dto.PageResult;
import com.microcourse.entity.DiscussionComment;

import java.util.List;

public interface DiscussionCommentService {

    List<DiscussionCommentVO> page(Long postId);

    DiscussionCommentVO create(CommentCreateRequest req, Long userId);

    void delete(Long id, Long userId);

    void like(Long id, Long userId);

    List<DiscussionCommentVO> buildCommentTree(List<DiscussionComment> flatList);

    // ========== P2-6 修复: 管理端评论操作 ==========

    /**
     * 管理端评论分页查询
     */
    PageResult<DiscussionCommentVO> pageAdmin(int page, int size, String keyword, Long postId);

    /**
     * 管理端删除评论（不校验所有权）
     */
    void deleteByAdmin(Long id);

    /**
     * 置顶/取消置顶评论
     */
    void pinComment(Long id, boolean pinned);
}