package com.microcourse.service;

import com.microcourse.dto.CommentCreateRequest;
import com.microcourse.dto.DiscussionCommentVO;
import com.microcourse.dto.PageResult;
import com.microcourse.entity.DiscussionComment;

import java.util.List;

public interface DiscussionCommentService {

    List<DiscussionCommentVO> page(Long postId);

    /**
     * P1-I-2026-08-15 · 讨论帖评论分页（替代全量 page 方法）
     *
     * @param postId 帖子 ID
     * @param page   0-based 页码
     * @param size   每页条数（由 Controller 校验上限）
     * @return 平铺评论 VO 列表 + 分页元数据
     */
    PageResult<DiscussionCommentVO> pagePaged(Long postId, int page, int size);

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