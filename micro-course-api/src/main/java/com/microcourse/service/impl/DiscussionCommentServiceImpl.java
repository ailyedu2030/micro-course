package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import com.microcourse.util.SecurityUtil;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbcTemplate;

    public DiscussionCommentServiceImpl(DiscussionCommentRepository commentRepository,
                                        DiscussionPostRepository postRepository,
                                        UserRepository userRepository,
                                        JdbcTemplate jdbcTemplate) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiscussionCommentVO> page(Long postId) {
        LambdaQueryWrapper<DiscussionComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DiscussionComment::getPostId, postId)
               .eq(DiscussionComment::getStatus, 1)
               .orderByAsc(DiscussionComment::getCreatedAt)
               .last("LIMIT 500"); // DISC-NEW-3 修复:硬上限防 OOM
        List<DiscussionComment> flatList = commentRepository.selectList(wrapper);

        // 获取帖子 OP 的 userId 用于 isOp 标记
        DiscussionPost post = postRepository.selectById(postId);
        Long opUserId = (post != null) ? post.getUserId() : null;

        return buildCommentTreeWithUsers(flatList, opUserId);
    }

    private List<DiscussionCommentVO> buildCommentTreeWithUsers(List<DiscussionComment> flatList, Long opUserId) {
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
        return buildCommentTreeWithBatchLoad(flatList, finalUserMap, opUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DiscussionCommentVO create(CommentCreateRequest req, Long userId) {
        // P1: 评论防刷—30 秒内只能评论一次
        DiscussionComment lastComment = commentRepository.selectOne(
                new LambdaQueryWrapper<DiscussionComment>()
                        .eq(DiscussionComment::getUserId, userId)
                        .orderByDesc(DiscussionComment::getCreatedAt)
                        .last("LIMIT 1"));
        if (lastComment != null && lastComment.getCreatedAt() != null
                && lastComment.getCreatedAt().plusSeconds(30).isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "评论过于频繁，请 30 秒后再试");
        }

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
        comment.setIsAnonymous(req.getIsAnonymous() != null ? req.getIsAnonymous() : false);
        comment.setIsTeacherReply(isTeacherOrAdmin);
        comment.setLikeCount(0);
        comment.setStatus(0); // P1#8: 评论默认待审核（0=PENDING），由审核员通过后才发布
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
        // 所有权校验：仅作者本人或 ADMIN/TEACHER 可删除(DISC-NEW-5 修复)
        User currentUser = userRepository.selectById(userId);
        boolean isAdminOrTeacher = currentUser != null
                && (currentUser.getRole() == com.microcourse.enums.UserRole.ADMIN
                        || currentUser.getRole() == com.microcourse.enums.UserRole.TEACHER);
        if (!comment.getUserId().equals(userId) && !isAdminOrTeacher) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        comment.setStatus(0);
        commentRepository.updateById(comment);
        // 原子递减帖子评论数
        if (comment.getPostId() != null) {
            postRepository.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.microcourse.entity.DiscussionPost>()
                            .eq(com.microcourse.entity.DiscussionPost::getId, comment.getPostId())
                            .setSql("comment_count = GREATEST(COALESCE(comment_count, 0) - 1, 0)"));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void like(Long id, Long userId) {
        DiscussionComment comment = commentRepository.selectById(id);
        if (comment == null || comment.getStatus() == 0) {
            throw new BusinessException(ErrorCode.DISCUSSION_COMMENT_NOT_FOUND);
        }
        // P1: 点赞去重—toggle 逻辑
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM discussion_comment_likes WHERE user_id = ? AND comment_id = ?",
                Integer.class, userId, id);
        if (count != null && count > 0) {
            // 已点赞 → 取消
            jdbcTemplate.update("DELETE FROM discussion_comment_likes WHERE user_id = ? AND comment_id = ?", userId, id);
            commentRepository.update(null,
                    new LambdaUpdateWrapper<DiscussionComment>()
                            .eq(DiscussionComment::getId, id)
                            .setSql("like_count = GREATEST(COALESCE(like_count, 0) - 1, 0)")
                            .set(DiscussionComment::getUpdatedAt, LocalDateTime.now()));
        } else {
            // 未点赞 → 点赞
            jdbcTemplate.update("INSERT INTO discussion_comment_likes (user_id, comment_id, created_at) VALUES (?, ?, ?)",
                    userId, id, java.sql.Timestamp.valueOf(LocalDateTime.now()));
            commentRepository.update(null,
                    new LambdaUpdateWrapper<DiscussionComment>()
                            .eq(DiscussionComment::getId, id)
                            .setSql("like_count = COALESCE(like_count, 0) + 1")
                            .set(DiscussionComment::getUpdatedAt, LocalDateTime.now()));
        }
    }

    @Override
    public List<DiscussionCommentVO> buildCommentTree(List<DiscussionComment> flatList) {
        // 调用内部批量加载版本（无 OP 信息时传 null）
        return buildCommentTreeWithUsers(flatList, null);
    }

    private List<DiscussionCommentVO> buildCommentTreeWithBatchLoad(List<DiscussionComment> flatList,
                                                       java.util.Map<Long, User> userMap,
                                                       Long opUserId) {
        List<DiscussionCommentVO> result = new ArrayList<>();
        for (DiscussionComment comment : flatList) {
            if (comment.getParentId() == null) {
                DiscussionCommentVO vo = convertToVO(comment, userMap, opUserId);
                vo.setChildren(buildChildren(comment.getId(), flatList, userMap, opUserId));
                result.add(vo);
            }
        }
        return result;
    }

    private List<DiscussionCommentVO> buildChildren(Long parentId, List<DiscussionComment> flatList,
                                                      java.util.Map<Long, User> userMap,
                                                      Long opUserId) {
        List<DiscussionCommentVO> children = new ArrayList<>();
        for (DiscussionComment comment : flatList) {
            if (parentId.equals(comment.getParentId())) {
                DiscussionCommentVO vo = convertToVO(comment, userMap, opUserId);
                vo.setChildren(buildChildren(comment.getId(), flatList, userMap, opUserId));
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
        vo.setIsAnonymous(comment.getIsAnonymous());
        vo.setIsTeacherReply(comment.getIsTeacherReply());
        vo.setLikeCount(comment.getLikeCount());
        vo.setCreatedAt(comment.getCreatedAt());
        // P0-1: 匿名评论隐藏用户信息
        if (Boolean.TRUE.equals(comment.getIsAnonymous())) {
            vo.setAuthorName("匿名用户");
            vo.setUserId(null);
        } else if (comment.getUserId() != null) {
            User user = userRepository.selectById(comment.getUserId());
            if (user != null) {
                vo.setAuthorName(user.getRealName() != null ? user.getRealName() : user.getUsername());
                vo.setRoleTag(user.getRole() != null ? user.getRole().name() : null);
            }
        }
        return vo;
    }

    private DiscussionCommentVO convertToVO(DiscussionComment comment, java.util.Map<Long, User> userMap, Long opUserId) {
        DiscussionCommentVO vo = new DiscussionCommentVO();
        vo.setId(comment.getId());
        vo.setPostId(comment.getPostId());
        vo.setParentId(comment.getParentId());
        vo.setUserId(comment.getUserId());
        vo.setContent(comment.getContent());
        vo.setIsAnonymous(comment.getIsAnonymous());
        vo.setIsTeacherReply(comment.getIsTeacherReply());
        vo.setLikeCount(comment.getLikeCount());
        vo.setCreatedAt(comment.getCreatedAt());
        // P0-1: 匿名评论隐藏用户信息
        if (Boolean.TRUE.equals(comment.getIsAnonymous())) {
            vo.setAuthorName("匿名用户");
            vo.setUserId(null);
        } else if (comment.getUserId() != null) {
            User user = userMap.get(comment.getUserId());
            if (user != null) {
                vo.setAuthorName(user.getRealName() != null ? user.getRealName() : user.getUsername());
                vo.setRoleTag(user.getRole() != null ? user.getRole().name() : null);
            }
        }
        // isOp: 评论者是帖子作者
        if (opUserId != null && comment.getUserId() != null && !Boolean.TRUE.equals(comment.getIsAnonymous())) {
            vo.setIsOp(comment.getUserId().equals(opUserId));
        } else {
            vo.setIsOp(false);
        }
        return vo;
    }
}