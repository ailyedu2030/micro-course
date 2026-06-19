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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
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
    @Transactional(readOnly = true)
    public List<DiscussionCommentVO> page(Long postId) {
        LambdaQueryWrapper<DiscussionComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DiscussionComment::getPostId, postId)
               .eq(DiscussionComment::getStatus, 1)
               .orderByAsc(DiscussionComment::getCreatedAt);
        List<DiscussionComment> flatList = commentRepository.selectList(wrapper);
        return buildCommentTreeWithUsers(flatList);
    }

    private List<DiscussionCommentVO> buildCommentTreeWithUsers(List<DiscussionComment> flatList) {
        if (flatList.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        // N+1 修复：批量预加载 user
        java.util.Map<Long, User> userMap = new java.util.HashMap<>();
        java.util.Set<Long> userIds = flatList.stream()
                .map(DiscussionComment::getUserId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (!userIds.isEmpty()) {
            userRepository.selectBatchIds(userIds).forEach(u -> userMap.put(u.getId(), u));
        }
        final java.util.Map<Long, User> finalUserMap = userMap;
        return buildCommentTreeWithBatchLoad(flatList, finalUserMap);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DiscussionCommentVO create(CommentCreateRequest req, Long userId) {
        // 检测当前用户角色是否为教师或管理员
        boolean isTeacherOrAdmin = false;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            for (var granted : auth.getAuthorities()) {
                String role = granted.getAuthority();
                if ("ROLE_TEACHER".equals(role) || "ROLE_ADMIN".equals(role)) {
                    isTeacherOrAdmin = true;
                    break;
                }
            }
        }

        DiscussionComment comment = new DiscussionComment();
        comment.setPostId(req.getPostId());
        comment.setParentId(req.getParentId());
        comment.setUserId(userId);
        comment.setContent(req.getContent());
        comment.setIsTeacherReply(isTeacherOrAdmin);
        comment.setLikeCount(0);
        comment.setStatus(1);
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());
        commentRepository.insert(comment);

        // 原子 SQL 增量 comment_count，避免并发读-改-写丢失更新
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<DiscussionPost> postUpdateWrapper =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        postUpdateWrapper.eq(DiscussionPost::getId, req.getPostId())
                .setSql("comment_count = COALESCE(comment_count, 0) + 1")
                .set(DiscussionPost::getUpdatedAt, LocalDateTime.now());
        postRepository.update(null, postUpdateWrapper);

        return convertToVO(comment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
    @Transactional(rollbackFor = Exception.class)
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
        // 调用内部批量加载版本
        return buildCommentTreeWithUsers(flatList);
    }

    private List<DiscussionCommentVO> buildCommentTreeWithBatchLoad(List<DiscussionComment> flatList,
                                                       java.util.Map<Long, User> userMap) {
        List<DiscussionCommentVO> result = new ArrayList<>();
        for (DiscussionComment comment : flatList) {
            if (comment.getParentId() == null) {
                DiscussionCommentVO vo = convertToVO(comment, userMap);
                vo.setChildren(buildChildren(comment.getId(), flatList, userMap));
                result.add(vo);
            }
        }
        return result;
    }

    private List<DiscussionCommentVO> buildChildren(Long parentId, List<DiscussionComment> flatList,
                                                      java.util.Map<Long, User> userMap) {
        List<DiscussionCommentVO> children = new ArrayList<>();
        for (DiscussionComment comment : flatList) {
            if (parentId.equals(comment.getParentId())) {
                DiscussionCommentVO vo = convertToVO(comment, userMap);
                vo.setChildren(buildChildren(comment.getId(), flatList, userMap));
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

    private DiscussionCommentVO convertToVO(DiscussionComment comment, java.util.Map<Long, User> userMap) {
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
            User user = userMap.get(comment.getUserId());
            if (user != null) {
                vo.setAuthorName(user.getUsername());
            }
        }
        return vo;
    }
}