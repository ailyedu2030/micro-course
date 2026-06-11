package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.CommentCreateRequest;
import com.microcourse.dto.DiscussionCommentVO;
import com.microcourse.entity.DiscussionComment;
import com.microcourse.entity.DiscussionPost;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.DiscussionCommentRepository;
import com.microcourse.repository.DiscussionPostRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.DiscussionCommentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DiscussionCommentServiceImpl implements DiscussionCommentService {

    private final DiscussionCommentRepository commentRepository;
    private final DiscussionPostRepository postRepository;
    private final UserRepository userRepository;

    public DiscussionCommentServiceImpl(DiscussionCommentRepository commentRepository,
                                        DiscussionPostRepository postRepository,
                                        UserRepository userRepository) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
    }

    @Override
    public List<DiscussionCommentVO> page(Long postId) {
        LambdaQueryWrapper<DiscussionComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DiscussionComment::getPostId, postId)
               .eq(DiscussionComment::getStatus, 1)
               .orderByAsc(DiscussionComment::getCreatedAt);
        List<DiscussionComment> flatList = commentRepository.selectList(wrapper);
        return buildCommentTree(flatList);
    }

    @Override
    @Transactional
    public DiscussionCommentVO create(CommentCreateRequest req, Long userId) {
        DiscussionComment comment = new DiscussionComment();
        comment.setPostId(req.getPostId());
        comment.setParentId(req.getParentId());
        comment.setUserId(userId);
        comment.setContent(req.getContent());
        comment.setIsTeacherReply(false);
        comment.setLikeCount(0);
        comment.setStatus(1);
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());
        commentRepository.insert(comment);

        // increment comment_count on discussion_posts
        DiscussionPost post = postRepository.selectById(req.getPostId());
        if (post != null) {
            post.setCommentCount(post.getCommentCount() != null ? post.getCommentCount() + 1 : 1);
            post.setUpdatedAt(LocalDateTime.now());
            postRepository.updateById(post);
        }

        return convertToVO(comment);
    }

    @Override
    public void delete(Long id, Long userId) {
        DiscussionComment comment = commentRepository.selectById(id);
        if (comment == null || comment.getStatus() == 0) {
            throw new BusinessException(ErrorCode.DISCUSSION_COMMENT_NOT_FOUND);
        }
        // 所有权校验：仅作者本人可删除
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        comment.setStatus(0);
        commentRepository.updateById(comment);
    }

    @Override
    public void like(Long id) {
        DiscussionComment comment = commentRepository.selectById(id);
        if (comment == null || comment.getStatus() == 0) {
            throw new BusinessException(ErrorCode.DISCUSSION_COMMENT_NOT_FOUND);
        }
        comment.setLikeCount(comment.getLikeCount() != null ? comment.getLikeCount() + 1 : 1);
        comment.setUpdatedAt(LocalDateTime.now());
        commentRepository.updateById(comment);
    }

    @Override
    public List<DiscussionCommentVO> buildCommentTree(List<DiscussionComment> flatList) {
        List<DiscussionCommentVO> result = new ArrayList<>();
        for (DiscussionComment comment : flatList) {
            if (comment.getParentId() == null) {
                DiscussionCommentVO vo = convertToVO(comment);
                vo.setChildren(buildChildren(comment.getId(), flatList));
                result.add(vo);
            }
        }
        return result;
    }

    private List<DiscussionCommentVO> buildChildren(Long parentId, List<DiscussionComment> flatList) {
        List<DiscussionCommentVO> children = new ArrayList<>();
        for (DiscussionComment comment : flatList) {
            if (parentId.equals(comment.getParentId())) {
                DiscussionCommentVO vo = convertToVO(comment);
                vo.setChildren(buildChildren(comment.getId(), flatList));
                children.add(vo);
            }
        }
        return children;
    }

    private DiscussionCommentVO convertToVO(DiscussionComment comment) {
        DiscussionCommentVO vo = new DiscussionCommentVO();
        vo.setId(comment.getId());
        vo.setPostId(comment.getPostId());
        vo.setParentId(comment.getParentId());
        vo.setUserId(comment.getUserId());
        vo.setContent(comment.getContent());
        vo.setIsTeacherReply(comment.getIsTeacherReply());
        vo.setLikeCount(comment.getLikeCount());
        vo.setCreatedAt(comment.getCreatedAt());
        if (comment.getUserId() != null) {
            User user = userRepository.selectById(comment.getUserId());
            if (user != null) {
                vo.setAuthorName(user.getUsername());
            }
        }
        return vo;
    }
}