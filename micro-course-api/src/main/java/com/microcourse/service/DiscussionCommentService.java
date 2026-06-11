package com.microcourse.service;

import com.microcourse.dto.CommentCreateRequest;
import com.microcourse.dto.DiscussionCommentVO;
import com.microcourse.entity.DiscussionComment;

import java.util.List;

public interface DiscussionCommentService {

    List<DiscussionCommentVO> page(Long postId);

    DiscussionCommentVO create(CommentCreateRequest req, Long userId);

    void delete(Long id, Long userId);

    void like(Long id);

    List<DiscussionCommentVO> buildCommentTree(List<DiscussionComment> flatList);
}