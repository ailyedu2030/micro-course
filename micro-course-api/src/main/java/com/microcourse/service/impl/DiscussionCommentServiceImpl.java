package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.CommentCreateRequest;
import com.microcourse.dto.DiscussionCommentVO;
import com.microcourse.dto.PageResult;
import com.microcourse.entity.DiscussionComment;
import com.microcourse.entity.DiscussionCommentLike;
import com.microcourse.entity.Course;
import com.microcourse.entity.DiscussionPost;
import com.microcourse.entity.User;
import com.microcourse.enums.NotificationType;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.DiscussionCommentLikeRepository;
import com.microcourse.repository.DiscussionCommentRepository;
import com.microcourse.repository.DiscussionPostRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.DiscussionCommentService;
import com.microcourse.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
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

    private static final Logger LOG = LoggerFactory.getLogger(DiscussionCommentServiceImpl.class);

    private final DiscussionCommentRepository commentRepository;
    private final DiscussionPostRepository postRepository;
    private final UserRepository userRepository;
    private final DiscussionCommentLikeRepository commentLikeRepository;
    /** P1-17: 评论频率限制 */
    private final StringRedisTemplate stringRedisTemplate;
    /** P1C-032: 通知服务 */
    private final NotificationService notificationService;
    /** P1C-032: 课程仓库，用于查询课程标题 */
    private final CourseRepository courseRepository;

    public DiscussionCommentServiceImpl(DiscussionCommentRepository commentRepository,
                                        DiscussionPostRepository postRepository,
                                        UserRepository userRepository,
                                        DiscussionCommentLikeRepository commentLikeRepository,
                                        StringRedisTemplate stringRedisTemplate,
                                        NotificationService notificationService,
                                        CourseRepository courseRepository) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.commentLikeRepository = commentLikeRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.notificationService = notificationService;
        this.courseRepository = courseRepository;
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
        // P1-17: 基于 Redis 的评论频率限制（每小时最多 20 条）
        String rateKey = "discussion:rate:" + userId;
        Long count = stringRedisTemplate.opsForValue().increment(rateKey);
        if (count == 1) {
            stringRedisTemplate.expire(rateKey, 1, java.util.concurrent.TimeUnit.HOURS);
        }
        if (count > 20) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "发帖太频繁，请稍后再试");
        }

        // C-23 修复: 检查帖子状态，只有已发布的帖子可评论
        DiscussionPost targetPost = postRepository.selectById(req.getPostId());
        checkCommentPostStatus(targetPost);

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
        // P1 安全修复: XSS 净化评论内容
        comment.setContent(com.microcourse.util.XssSanitizer.sanitize(req.getContent()));
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

        // P1C-032: 讨论区有新回复 → 通知帖子作者（自己回复自己不通知）
        try {
            DiscussionPost post = postRepository.selectById(req.getPostId());
            if (post != null && post.getUserId() != null && !post.getUserId().equals(userId)) {
                Course course = courseRepository.selectById(post.getCourseId());
                String courseTitle = course != null ? course.getTitle() : "课程";
                notificationService.notifyAsync(post.getUserId(), NotificationType.DISCUSSION_REPLY,
                        "讨论区有新回复",
                        "您在《" + courseTitle + "》的帖子「" + truncateTitle(post.getTitle()) + "」有新回复",
                        post.getCourseId());
            }
        } catch (Exception e) {
            LOG.warn("[DiscussionComment] 发送回复通知失败 postId={}", req.getPostId(), e);
        }

        return convertToVO(comment);
    }

    /**
     * 【根因】C-23: delete() 中 comment.getStatus() == 0 时统一抛 NOT_FOUND
     * 【修复】区分 PENDING(0) → 提示"评论正在审核中"；null/deleted → NOT_FOUND
     * 【防止再发】统一使用 checkCommentStatus 模式
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long userId) {
        DiscussionComment comment = commentRepository.selectById(id);
        checkCommentStatus(comment);
        // 所有权校验：仅作者本人或 ADMIN/TEACHER 可删除(DISC-NEW-5 修复)
        User currentUser = userRepository.selectById(userId);
        boolean isAdminOrTeacher = currentUser != null
                && (currentUser.getRole() == com.microcourse.enums.UserRole.ADMIN
                        || currentUser.getRole() == com.microcourse.enums.UserRole.TEACHER);
        if (!comment.getUserId().equals(userId) && !isAdminOrTeacher) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        // P1-I: 使用 LambdaUpdateWrapper 统一软删除，与项目 @TableLogic 模式保持一致
        commentRepository.update(null,
                new LambdaUpdateWrapper<DiscussionComment>()
                        .eq(DiscussionComment::getId, id)
                        .set(DiscussionComment::getDeletedAt, LocalDateTime.now())
                        .set(DiscussionComment::getUpdatedAt, LocalDateTime.now()));
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
        checkCommentStatus(comment);
        // P1-I: 使用 MyBatis-Plus LambdaQueryWrapper 替代 JdbcTemplate 硬编码 SQL
        long likeCount = commentLikeRepository.selectCount(
                new LambdaQueryWrapper<DiscussionCommentLike>()
                        .eq(DiscussionCommentLike::getUserId, userId)
                        .eq(DiscussionCommentLike::getCommentId, id));
        if (likeCount > 0) {
            // 已点赞 → 取消
            commentLikeRepository.delete(
                    new LambdaUpdateWrapper<DiscussionCommentLike>()
                            .eq(DiscussionCommentLike::getUserId, userId)
                            .eq(DiscussionCommentLike::getCommentId, id));
            commentRepository.update(null,
                    new LambdaUpdateWrapper<DiscussionComment>()
                            .eq(DiscussionComment::getId, id)
                            .setSql("like_count = GREATEST(COALESCE(like_count, 0) - 1, 0)")
                            .set(DiscussionComment::getUpdatedAt, LocalDateTime.now()));
        } else {
            // 未点赞 → 点赞
            DiscussionCommentLike like = new DiscussionCommentLike();
            like.setUserId(userId);
            like.setCommentId(id);
            like.setCreatedAt(LocalDateTime.now());
            commentLikeRepository.insert(like);
            commentRepository.update(null,
                    new LambdaUpdateWrapper<DiscussionComment>()
                            .eq(DiscussionComment::getId, id)
                            .setSql("like_count = COALESCE(like_count, 0) + 1")
                            .set(DiscussionComment::getUpdatedAt, LocalDateTime.now()));
        }
    }

    // ========== P2-6 修复: 管理端评论操作 ==========

    @Override
    @Transactional(readOnly = true)
    public PageResult<DiscussionCommentVO> pageAdmin(int page, int size, String keyword, Long postId) {
        LambdaQueryWrapper<DiscussionComment> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(DiscussionComment::getContent, keyword);
        }
        if (postId != null) {
            wrapper.eq(DiscussionComment::getPostId, postId);
        }
        wrapper.orderByDesc(DiscussionComment::getCreatedAt);

        IPage<DiscussionComment> pageResult = commentRepository.selectPage(new Page<>(page, size), wrapper);
        List<DiscussionCommentVO> voList = pageResult.getRecords().stream()
                .map(c -> convertToVO(c))
                .collect(Collectors.toList());
        return PageResult.of(voList, pageResult.getTotal(), (int) pageResult.getCurrent() - 1, (int) pageResult.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByAdmin(Long id) {
        DiscussionComment comment = commentRepository.selectById(id);
        if (comment == null) {
            throw new BusinessException(ErrorCode.DISCUSSION_COMMENT_NOT_FOUND);
        }
        commentRepository.update(null,
                new LambdaUpdateWrapper<DiscussionComment>()
                        .eq(DiscussionComment::getId, id)
                        .set(DiscussionComment::getDeletedAt, LocalDateTime.now())
                        .set(DiscussionComment::getUpdatedAt, LocalDateTime.now()));
        if (comment.getPostId() != null) {
            postRepository.update(null,
                    new LambdaUpdateWrapper<DiscussionPost>()
                            .eq(DiscussionPost::getId, comment.getPostId())
                            .setSql("comment_count = GREATEST(COALESCE(comment_count, 0) - 1, 0)"));
        }
        LOG.info("[P2-6] 管理员删除评论 id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pinComment(Long id, boolean pinned) {
        DiscussionComment comment = commentRepository.selectById(id);
        if (comment == null) {
            throw new BusinessException(ErrorCode.DISCUSSION_COMMENT_NOT_FOUND);
        }
        int newStatus = pinned ? 2 : 1;
        commentRepository.update(null,
                new LambdaUpdateWrapper<DiscussionComment>()
                        .eq(DiscussionComment::getId, id)
                        .set(DiscussionComment::getStatus, newStatus)
                        .set(DiscussionComment::getUpdatedAt, LocalDateTime.now()));
        LOG.info("[P2-6] 管理员{}评论 id={}", pinned ? "置顶" : "取消置顶", id);
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

    /**
     * @deprecated 单参数版本仅供单条创建返回使用;批量查询请用3参数重载(带userMap)
     */
    @Deprecated
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

    /**
     * 【根因】C-23: 评论前未检查帖子状态，导致 PENDING/REJECTED 帖子仍可接收评论
     * 【修复】创建评论前检查帖子状态：PENDING/REJECTED 时拒绝评论
     * 【防止再发】所有涉及帖子状态的评论操作必须调用此方法
     */
    private void checkCommentPostStatus(DiscussionPost post) {
        if (post == null || post.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.DISCUSSION_POST_NOT_FOUND);
        }
        Integer status = post.getStatus();
        if (status == null) {
            throw new BusinessException(ErrorCode.DISCUSSION_POST_NOT_FOUND);
        }
        if (status == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "帖子正在审核中，暂时无法评论");
        }
        if (status == 2) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "帖子已被驳回，暂时无法评论");
        }
    }

    /**
     * 【根因】C-23: 评论操作中 status==0 统一抛 NOT_FOUND，将"待审核"与"不存在"混为一谈
     * 【修复】区分 PENDING(0) → 可感知提示，null/deleted → NOT_FOUND
     * 【防止再发】所有涉及评论状态的操作必须调用此方法
     */
    private void checkCommentStatus(DiscussionComment comment) {
        if (comment == null || comment.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.DISCUSSION_COMMENT_NOT_FOUND);
        }
        Integer status = comment.getStatus();
        if (status == null) {
            throw new BusinessException(ErrorCode.DISCUSSION_COMMENT_NOT_FOUND);
        }
        if (status == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "评论正在审核中，暂时无法操作");
        }
    }

    /**
     * P1C-032: 截断帖子标题用于通知消息（超过 30 字加省略号）
     */
    private static String truncateTitle(String title) {
        if (title == null) return "";
        return title.length() > 30 ? title.substring(0, 30) + "…" : title;
    }
}
