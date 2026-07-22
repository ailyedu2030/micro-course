package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.DiscussionPostVO;
import com.microcourse.dto.DiscussionCommentVO;
import com.microcourse.dto.DiscussionPageQuery;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.PostCreateRequest;
import com.microcourse.dto.PostUpdateRequest;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseChapter;
import com.microcourse.entity.DiscussionPost;
import com.microcourse.entity.DiscussionComment;
import com.microcourse.entity.User;
import com.microcourse.enums.NotificationType;
import com.microcourse.enums.UserRole;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.DiscussionPostRepository;
import com.microcourse.repository.DiscussionCommentRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.service.DiscussionPostService;
import com.microcourse.service.NotificationService;
import com.microcourse.util.SecurityUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DiscussionPostServiceImpl implements DiscussionPostService {

    private final DiscussionPostRepository postRepository;
    private final DiscussionCommentRepository commentRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final CourseChapterRepository courseChapterRepository;
    private final EnrollmentRepository enrollmentRepository;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DiscussionPostServiceImpl.class);

    /** P1-17: 发帖频率限制 */
    private final StringRedisTemplate stringRedisTemplate;
    /** P1I-023: 通知服务 */
    private final NotificationService notificationService;

    public DiscussionPostServiceImpl(DiscussionPostRepository postRepository,
                                      DiscussionCommentRepository commentRepository,
                                      UserRepository userRepository,
                                      CourseRepository courseRepository,
                                      CourseChapterRepository courseChapterRepository,
                                      EnrollmentRepository enrollmentRepository,
                                      StringRedisTemplate stringRedisTemplate,
                                      NotificationService notificationService) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.courseChapterRepository = courseChapterRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.notificationService = notificationService;
    }

       @Override
    @Transactional(readOnly = true)
    public PageResult<DiscussionPostVO> page(Long chapterId, int page, int size) {
        // 当前登录用户（讨论列表可能匿名访问，取不到时按 null 处理）
        Long currentUserId;
        try {
            currentUserId = com.microcourse.util.SecurityUtil.getCurrentUserId();
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(DiscussionPostServiceImpl.class)
                .warn("获取当前用户ID失败(匿名访问): {}", e.getMessage());
            currentUserId = null;
        }
        final Long uid = currentUserId;

        LambdaQueryWrapper<DiscussionPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(chapterId != null, DiscussionPost::getChapterId, chapterId);
        // 所有用户可见已发布(status=1)；作者本人可见自己的所有状态(含 PENDING/REJECTED)
        if (uid != null) {
            wrapper.and(w -> w.eq(DiscussionPost::getStatus, 1)
                    .or(w2 -> w2.eq(DiscussionPost::getUserId, uid)));
        } else {
            wrapper.eq(DiscussionPost::getStatus, 1);
        }
        wrapper.orderByDesc(DiscussionPost::getIsPinned)
               .orderByDesc(DiscussionPost::getCreatedAt);
        IPage<DiscussionPost> ipage = postRepository.selectPage(
                new Page<>(page + 1, size), wrapper);

        // N+1 修复：批量预加载 user
        java.util.Map<Long, User> userMap = new java.util.HashMap<>();
        java.util.Set<Long> userIds = ipage.getRecords().stream()
                .map(DiscussionPost::getUserId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (!userIds.isEmpty()) {
            userRepository.selectBatchIds(userIds).forEach(u -> userMap.put(u.getId(), u));
        }
        final java.util.Map<Long, User> finalUserMap = userMap;

        List<DiscussionPostVO> vos = ipage.getRecords().stream()
                .map(p -> convertToVO(p, finalUserMap)).collect(Collectors.toList());
        PageResult<DiscussionPostVO> result = new PageResult<>();
        result.setItems(vos);
        result.setPage((int) ipage.getCurrent() - 1);
        result.setSize((int) ipage.getSize());
        result.setTotalElements(ipage.getTotal());
        result.setTotalPages(ipage.getPages());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<DiscussionPostVO> pageAdmin(DiscussionPageQuery query) {
        LambdaQueryWrapper<DiscussionPost> wrapper = new LambdaQueryWrapper<>();

        // keyword搜索：title或content模糊匹配(通配符转义,防 LIKE 注入 DF-002)
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String escaped = query.getKeyword()
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
            wrapper.and(w -> w.like(DiscussionPost::getTitle, escaped)
                    .or().like(DiscussionPost::getContent, escaped));
        }

        // 按courseId筛选
        wrapper.eq(query.getCourseId() != null, DiscussionPost::getCourseId, query.getCourseId());

        // 按status筛选（前端传字符串：PENDING/PUBLISHED/REJECTED/DELETED，数据库存整数）
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            Integer statusVal = switch (query.getStatus()) {
                case "PENDING" -> 0;
                case "PUBLISHED" -> 1;
                case "REJECTED" -> 2;
                case "DELETED" -> 3;
                default -> null;
            };
            wrapper.eq(statusVal != null, DiscussionPost::getStatus, statusVal);
        }

        wrapper.orderByDesc(DiscussionPost::getIsPinned)
               .orderByDesc(DiscussionPost::getCreatedAt);

        IPage<DiscussionPost> ipage = postRepository.selectPage(
                new Page<>(query.getPage() + 1, query.getSize()), wrapper);

        // 批量预加载 user + course
        java.util.Map<Long, User> userMap = new java.util.HashMap<>();
        java.util.Map<Long, Course> courseMap = new java.util.HashMap<>();
        java.util.Set<Long> userIds = ipage.getRecords().stream()
                .map(DiscussionPost::getUserId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Set<Long> courseIds = ipage.getRecords().stream()
                .map(DiscussionPost::getCourseId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        if (!userIds.isEmpty()) {
            userRepository.selectBatchIds(userIds).forEach(u -> userMap.put(u.getId(), u));
        }
        if (!courseIds.isEmpty()) {
            courseRepository.selectBatchIds(courseIds).forEach(c -> courseMap.put(c.getId(), c));
        }
        final java.util.Map<Long, User> finalUserMap = userMap;
        final java.util.Map<Long, Course> finalCourseMap = courseMap;

        List<DiscussionPostVO> vos = ipage.getRecords().stream()
                .map(p -> convertToVOForAdmin(p, finalUserMap, finalCourseMap))
                .collect(Collectors.toList());

        PageResult<DiscussionPostVO> result = new PageResult<>();
        result.setItems(vos);
        result.setPage((int) ipage.getCurrent() - 1);
        result.setSize((int) ipage.getSize());
        result.setTotalElements(ipage.getTotal());
        result.setTotalPages(ipage.getPages());
        return result;
    }

    private DiscussionPostVO convertToVOForAdmin(DiscussionPost post,
                                                  java.util.Map<Long, User> userMap,
                                                  java.util.Map<Long, Course> courseMap) {
        DiscussionPostVO vo = new DiscussionPostVO();
        vo.setId(post.getId());
        vo.setCourseId(post.getCourseId());
        vo.setChapterId(post.getChapterId());
        vo.setUserId(post.getUserId());
        vo.setTitle(post.getTitle());
        vo.setContent(post.getContent());
        vo.setIsAnonymous(post.getIsAnonymous());
        vo.setIsPinned(post.getIsPinned());
        vo.setIsEssence(post.getIsEssence());
        vo.setCommentCount(post.getCommentCount());
        vo.setLikeCount(post.getLikeCount());
        vo.setCreatedAt(post.getCreatedAt());

        // status: Integer → String (防御 null 值 NPE)
        int statusCode = post.getStatus() != null ? post.getStatus() : 0;
        String statusStr = switch (statusCode) {
            case 0 -> "PENDING";
            case 1 -> "PUBLISHED";
            case 2 -> "REJECTED";
            case 3 -> "DELETED";
            default -> "UNKNOWN";
        };
        vo.setStatus(statusStr);

        // courseName
        if (post.getCourseId() != null) {
            Course course = courseMap.get(post.getCourseId());
            if (course != null) {
                vo.setCourseName(course.getTitle());
            }
        }

        // authorName
        if (post.getUserId() != null) {
            User author = userMap.get(post.getUserId());
            if (author != null) {
                if (Boolean.TRUE.equals(post.getIsAnonymous())) {
                    vo.setAuthorName("匿名用户");
                    vo.setUserId(null);
                } else {
                    vo.setAuthorName(author.getRealName() != null ? author.getRealName() : author.getUsername());
                }
            }
        }

        return vo;
    }

    @Override
    @Transactional(readOnly = true)
    public DiscussionPostVO getById(Long id) {
        DiscussionPost post = postRepository.selectById(id);
        // C-23 修复: 区分"不存在"、"待审核"、"已驳回"
        if (post == null || post.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.DISCUSSION_POST_NOT_FOUND);
        }
        // 管理员/教师/教务可查看待审核帖子，学生不可
        boolean isManager = SecurityUtil.isAdmin() || SecurityUtil.hasRole("TEACHER") || SecurityUtil.hasRole("ACADEMIC");
        if (post.getStatus() != null) {
            if (post.getStatus() == 0 && !isManager) {
                // P1-C: 用专门的"帖子审核中"错误码, 而非通用 BAD_REQUEST_PARAM
                throw new BusinessException(ErrorCode.DISCUSSION_POST_PENDING_REVIEW);
            }
            if (post.getStatus() == 2 && !isManager) {
                // P1-C: 用专门的"帖子已驳回"错误码
                throw new BusinessException(ErrorCode.DISCUSSION_POST_REJECTED);
            }
        }

        // N+1 修复：批量预加载 post author 和 comment authors
        java.util.Map<Long, User> userMap = new java.util.HashMap<>();
        java.util.Set<Long> userIds = new java.util.HashSet<>();
        if (post.getUserId() != null) {
            userIds.add(post.getUserId());
        }

        // 查评论列表并构建树结构（PERF-006: 添加 LIMIT 防止全量加载）
        LambdaQueryWrapper<DiscussionComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DiscussionComment::getPostId, id)
               .eq(DiscussionComment::getStatus, 1)
               .orderByAsc(DiscussionComment::getCreatedAt)
               .last("LIMIT 200");
        List<DiscussionComment> comments = commentRepository.selectList(wrapper);

        comments.forEach(c -> {
            if (c.getUserId() != null) userIds.add(c.getUserId());
        });

        if (!userIds.isEmpty()) {
            userRepository.selectBatchIds(userIds).forEach(u -> userMap.put(u.getId(), u));
        }
        final java.util.Map<Long, User> finalUserMap = userMap;

        DiscussionPostVO vo = convertToVO(post, finalUserMap);
        vo.setChildren(buildCommentTree(comments, finalUserMap, post.getUserId()));

        // P0-4: 设置 isOwner，让匿名帖子作者也能删除自己的帖子
        try {
            Long currentUserId = com.microcourse.util.SecurityUtil.getCurrentUserId();
            vo.setIsOwner(post.getUserId() != null && post.getUserId().equals(currentUserId));
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(DiscussionPostServiceImpl.class)
                .warn("设置isOwner失败(未登录): {}", e.getMessage());
            vo.setIsOwner(false);
        }

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DiscussionPostVO create(PostCreateRequest req, Long userId) {
        // P1-17: 基于 Redis 的发帖频率限制（每小时最多 20 帖）
        String rateKey = "discussion:rate:" + userId;
        Long count = stringRedisTemplate.opsForValue().increment(rateKey);
        if (count == 1) {
            stringRedisTemplate.expire(rateKey, 1, java.util.concurrent.TimeUnit.HOURS);
        }
        if (count > 20) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "发帖太频繁，请稍后再试");
        }

        // 1. 先解析 courseId：优先用请求中的，否则从 chapterId 反查
        Long resolvedCourseId = req.getCourseId();
        if (resolvedCourseId == null && req.getChapterId() != null) {
            CourseChapter chapter = courseChapterRepository.selectById(req.getChapterId());
            if (chapter != null) resolvedCourseId = chapter.getCourseId();
        }

        // 2. 选课检查：STUDENT 只能发己选课程的讨论（OP-0105 修复：仅 APPROVED/COMPLETED 状态有效）
        if (SecurityUtil.hasRole("STUDENT")) {
            if (resolvedCourseId == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "无法确定课程，请指定课程或章节");
            }
            long enrolledCount = enrollmentRepository.selectCount(
                    new LambdaQueryWrapper<com.microcourse.entity.Enrollment>()
                            .eq(com.microcourse.entity.Enrollment::getUserId, userId)
                            .eq(com.microcourse.entity.Enrollment::getCourseId, resolvedCourseId)
                            .in(com.microcourse.entity.Enrollment::getEnrollmentStatus, "APPROVED", "COMPLETED"));
            if (enrolledCount == 0) {
                throw new BusinessException(ErrorCode.NOT_ENROLLED, "请先选课后再参与讨论");
            }
        }

        // 3. 发帖频率限制—30 秒内只能发一帖,防止刷帖
        DiscussionPost lastPost = postRepository.selectOne(
                new LambdaQueryWrapper<DiscussionPost>()
                        .eq(DiscussionPost::getUserId, userId)
                        .orderByDesc(DiscussionPost::getCreatedAt)
                        .last("LIMIT 1"));
        if (lastPost != null && lastPost.getCreatedAt() != null
                && lastPost.getCreatedAt().plusSeconds(30).isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "发帖过于频繁,请 30 秒后再试");
        }

        // 4. 验证 chapterId 存在
        if (req.getChapterId() != null) {
            CourseChapter chapter = courseChapterRepository.selectById(req.getChapterId());
            if (chapter == null) {
                throw new BusinessException(ErrorCode.CHAPTER_NOT_FOUND);
            }
        }

        DiscussionPost post = new DiscussionPost();
        post.setCourseId(resolvedCourseId);
        post.setChapterId(req.getChapterId());
        post.setUserId(userId);
        // P1 安全修复: XSS 净化 — 标题使用纯文本净化（不应含 HTML），内容允许安全标签
        post.setTitle(com.microcourse.util.XssSanitizer.sanitizePlainText(req.getTitle()));
        post.setContent(com.microcourse.util.XssSanitizer.sanitize(req.getContent()));
        post.setIsAnonymous(req.getIsAnonymous() != null ? req.getIsAnonymous() : false);
        post.setIsPinned(false);
        post.setIsEssence(false);
        post.setCommentCount(0);
        post.setLikeCount(0);
        post.setStatus(0); // PENDING 审核
        post.setCreatedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
        postRepository.insert(post);
        return convertToVO(post);
    }

    /**
     * 【根因】update() 中 post.getStatus() == 0 时统一抛 NOT_FOUND，将"待审核"与"不存在"混为一谈
     * 【修复】区分 PENDING(0) → 提示"帖子正在审核中"；REJECTED(2) → 提示"帖子已被驳回"；null/deleted → NOT_FOUND
     * 【防止再发】所有涉及帖子状态的操作使用统一的状态检查模式
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DiscussionPostVO update(Long id, PostUpdateRequest request, Long userId) {
        DiscussionPost post = postRepository.selectById(id);
        checkPostStatus(post);
        // 检查是否是作者或管理员
        User user = userRepository.selectById(userId);
        boolean isAdmin = user != null && user.getRole() == UserRole.ADMIN;
        if (!post.getUserId().equals(userId) && !isAdmin) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        if (request.getTitle() != null) {
            post.setTitle(com.microcourse.util.XssSanitizer.sanitizePlainText(request.getTitle()));
        }
        if (request.getContent() != null) {
            post.setContent(com.microcourse.util.XssSanitizer.sanitize(request.getContent()));
        }
        if (request.getIsAnonymous() != null) {
            post.setIsAnonymous(request.getIsAnonymous());
        }
        post.setUpdatedAt(LocalDateTime.now());
        postRepository.updateById(post);
        return convertToVO(post);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long userId) {
        DiscussionPost post = postRepository.selectById(id);
        checkPostStatus(post);
        // 检查是否是作者、管理员、教务处或本课程教师
        boolean isAuthor = post.getUserId().equals(userId);
        boolean isManager = SecurityUtil.isAdmin()
                || SecurityUtil.hasRole("ACADEMIC")
                || (SecurityUtil.hasRole("TEACHER") && post.getCourseId() != null
                    && isCurrentUserCourseTeacher(userId, post.getCourseId()));
        if (!isAuthor && !isManager) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        // 软删：使用 @TableLogic 的 deleteById 触发 deletedAt 填充,而非手动设 status=0
        postRepository.deleteById(id);
    }

    /**
     * 检查当前用户是否是该课程的授课教师
     */
    private boolean isCurrentUserCourseTeacher(Long userId, Long courseId) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Course> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(Course::getId, courseId).eq(Course::getTeacherId, userId);
        return courseRepository.selectCount(wrapper) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pin(Long id) {
        DiscussionPost post = postRepository.selectById(id);
        checkPostStatus(post);
        // 仅课程授课教师可置顶
        if (post.getCourseId() != null && !SecurityUtil.isAdmin()) {
            Course c = courseRepository.selectById(post.getCourseId());
            if (c != null && !SecurityUtil.isOwnerOrAdmin(c.getTeacherId())) {
                throw new BusinessException(ErrorCode.NO_PERMISSION);
            }
        }
        // is_pinned = !is_pinned (toggle)
        post.setIsPinned(!post.getIsPinned());
        post.setUpdatedAt(LocalDateTime.now());
        postRepository.updateById(post);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePin(Long id, boolean pinned) {
        DiscussionPost post = postRepository.selectById(id);
        checkPostStatus(post);
        // R12 P1-C-6: 仅课程授课教师可置顶
        if (post.getCourseId() != null && !SecurityUtil.isAdmin()) {
            Course c = courseRepository.selectById(post.getCourseId());
            if (c != null && !SecurityUtil.isOwnerOrAdmin(c.getTeacherId())) {
                throw new BusinessException(ErrorCode.NO_PERMISSION);
            }
        }
        post.setIsPinned(pinned);
        post.setUpdatedAt(LocalDateTime.now());
        postRepository.updateById(post);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEssence(Long id, boolean essence) {
        DiscussionPost post = postRepository.selectById(id);
        checkPostStatus(post);
        // R12 P1-C-6: 仅课程授课教师可设精华
        if (post.getCourseId() != null && !SecurityUtil.isAdmin()) {
            Course c = courseRepository.selectById(post.getCourseId());
            if (c != null && !SecurityUtil.isOwnerOrAdmin(c.getTeacherId())) {
                throw new BusinessException(ErrorCode.NO_PERMISSION);
            }
        }
        post.setIsEssence(essence);
        post.setUpdatedAt(LocalDateTime.now());
        postRepository.updateById(post);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectWithReason(Long id, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "驳回原因不能为空");
        }
        DiscussionPost post = postRepository.selectById(id);
        if (post == null) {
            throw new BusinessException(ErrorCode.DISCUSSION_POST_NOT_FOUND);
        }
        /* ---- 【CR-1 修复】讨论审核 assertNotSelf ---- */
        /* 【根因】课程审核有 assertNotSelf 防止教师审批自己的课程，但讨论审核没有此机制 */
        /* 【修复】增加 assertNotSelf 检查：发帖者与审核者不能是同一人 */
        /* 【防止再发】所有审核类操作统一使用 assertNotSelf 阻断自审批 */
        SecurityUtil.assertNotSelf(SecurityUtil.getCurrentUserId(), post.getUserId(), "不能审核自己的讨论帖");
        // P1C-060: 只能驳回待审核(PENDING=0)或已发布(PUBLISHED=1)的帖子
        int current = post.getStatus() != null ? post.getStatus() : 0;
        if (current != 0 && current != 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "当前状态的讨论无法驳回");
        }
        post.setStatus(2); // REJECTED
        post.setRejectReason(reason.trim());
        post.setUpdatedAt(LocalDateTime.now());
        postRepository.updateById(post);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, String status) {
        DiscussionPost post = postRepository.selectById(id);
        if (post == null) {
            throw new BusinessException(ErrorCode.DISCUSSION_POST_NOT_FOUND);
        }
        /* ---- 【CR-1 修复】讨论审核 assertNotSelf ---- */
        /* 【根因】课程审核有 assertNotSelf 防止教师审批自己的课程，但讨论审核没有此机制 */
        /* 【修复】增加 assertNotSelf 检查：发帖者与审核者不能是同一人 */
        /* 【防止再发】所有审核类操作统一使用 assertNotSelf 阻断自审批 */
        if ("APPROVED".equals(status) || "PUBLISHED".equals(status)) {
            SecurityUtil.assertNotSelf(SecurityUtil.getCurrentUserId(), post.getUserId(), "不能审核自己的讨论帖");
        }
        // P1#10: REJECTED 与 DELETED 拆为不同状态码（2=REJECTED 审核驳回，3=DELETED 用户删除）
        Integer target = switch (status) {
            case "APPROVED", "PUBLISHED" -> 1;   // APPROVED/PUBLISHED
            case "REJECTED" -> 2;                // 审核驳回
            case "PENDING"  -> 0;                // 待审核
            case "DELETED"  -> 3;                // 用户删除
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "无效的讨论状态");
        };

        // P1#9: 合法状态转换校验
        //   0 (PENDING)  → 1 (APPROVED), 2 (REJECTED)
        //   1 (APPROVED) → 2 (REJECTED), 3 (DELETED)
        //   2 (REJECTED) → 0 (PENDING) 允许重新提交
        //   3 (DELETED)  终态，不可再转换
        int current = post.getStatus() != null ? post.getStatus() : 0;
        boolean allowed = switch (current) {
            case 0 -> target == 1 || target == 2;
            case 1 -> target == 2 || target == 3;
            case 2 -> target == 0;
            default -> false;
        };
        if (!allowed) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "不允许的状态转换");
        }

        post.setStatus(target);
        post.setUpdatedAt(LocalDateTime.now());
        postRepository.updateById(post);

        // P1I-023: 审核通过时通知发帖人
        if (target == 1 && post.getUserId() != null) {
            try {
                String courseTitle = "";
                if (post.getCourseId() != null) {
                    Course course = courseRepository.selectById(post.getCourseId());
                    if (course != null) {
                        courseTitle = "《" + course.getTitle() + "》";
                    }
                }
                notificationService.notifyAsync(
                        post.getUserId(),
                        NotificationType.DISCUSSION_POST_APPROVED,
                        "讨论帖已通过审核",
                        "您在" + courseTitle + "的帖子「" + (post.getTitle() != null ? post.getTitle() : "") + "」已通过审核",
                        post.getCourseId()
                );
            } catch (Exception e) {
                log.warn("[DiscussionPost] 发送审核通过通知失败 postId={}", id, e);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void like(Long postId, Long userId) {
        DiscussionPost post = postRepository.selectById(postId);
        checkPostStatus(post);
        postRepository.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<DiscussionPost>()
                        .eq(DiscussionPost::getId, postId)
                        .setSql("like_count = COALESCE(like_count, 0) + 1"));
    }

    /**
     * 【根因】C-23: 多个方法中 status==0 统一抛 NOT_FOUND，将"待审核"与"不存在"混为一谈
     * 【修复】统一状态检查：PENDING(0) → 可感知提示，REJECTED(2) → 可感知提示，null/deleted → NOT_FOUND
     * 【防止再发】所有涉及帖子状态的操作必须调用此方法
     */
    private void checkPostStatus(DiscussionPost post) {
        if (post == null || post.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.DISCUSSION_POST_NOT_FOUND);
        }
        Integer status = post.getStatus();
        if (status == null) {
            throw new BusinessException(ErrorCode.DISCUSSION_POST_NOT_FOUND);
        }
        if (status == 0) { // PENDING
            // P1-C: 用专门的"帖子审核中"错误码, 而非通用 BAD_REQUEST_PARAM
            throw new BusinessException(ErrorCode.DISCUSSION_POST_PENDING_REVIEW);
        }
        if (status == 2) { // REJECTED
            // P1-C: 用专门的"帖子已驳回"错误码, 而非通用 BAD_REQUEST_PARAM
            throw new BusinessException(ErrorCode.DISCUSSION_POST_REJECTED);
        }
    }

    private DiscussionPostVO convertToVO(DiscussionPost post) {
        DiscussionPostVO vo = new DiscussionPostVO();
        vo.setId(post.getId());
        vo.setCourseId(post.getCourseId());
        vo.setChapterId(post.getChapterId());
        vo.setUserId(post.getUserId());
        vo.setTitle(post.getTitle());
        vo.setContent(post.getContent());
        vo.setIsAnonymous(post.getIsAnonymous());
        vo.setIsPinned(post.getIsPinned());
        vo.setIsEssence(post.getIsEssence());
        vo.setCommentCount(post.getCommentCount());
        vo.setLikeCount(post.getLikeCount());
        vo.setCreatedAt(post.getCreatedAt());

        // 联查 authorName
        if (post.getUserId() != null) {
            User author = userRepository.selectById(post.getUserId());
            if (author != null) {
                // P1C-028: TEACHER/ADMIN 可见匿名帖子真实身份
                if (Boolean.TRUE.equals(post.getIsAnonymous()) && !isTeacherOrAdminForCourse(post.getCourseId())) {
                    vo.setAuthorName("匿名用户");
                    vo.setUserId(null);
                } else {
                    vo.setAuthorName(author.getRealName() != null ? author.getRealName() : author.getUsername());
                }
            }
        }

        return vo;
    }

    private DiscussionPostVO convertToVO(DiscussionPost post, java.util.Map<Long, User> userMap) {
        DiscussionPostVO vo = new DiscussionPostVO();
        vo.setId(post.getId());
        vo.setCourseId(post.getCourseId());
        vo.setChapterId(post.getChapterId());
        vo.setUserId(post.getUserId());
        vo.setTitle(post.getTitle());
        vo.setContent(post.getContent());
        vo.setIsAnonymous(post.getIsAnonymous());
        vo.setIsPinned(post.getIsPinned());
        vo.setIsEssence(post.getIsEssence());
        vo.setCommentCount(post.getCommentCount());
        vo.setLikeCount(post.getLikeCount());
        vo.setCreatedAt(post.getCreatedAt());

        // 联查 authorName（使用预加载的 Map）
        if (post.getUserId() != null) {
            User author = userMap.get(post.getUserId());
            if (author != null) {
                // P1C-028: TEACHER/ADMIN 可见匿名帖子真实身份
                if (Boolean.TRUE.equals(post.getIsAnonymous()) && !isTeacherOrAdminForCourse(post.getCourseId())) {
                    vo.setAuthorName("匿名用户");
                    vo.setUserId(null);
                } else {
                    vo.setAuthorName(author.getRealName() != null ? author.getRealName() : author.getUsername());
                }
            }
        }

        return vo;
    }

    /**
     * P1C-028: 判断当前用户是否为课程的授课教师或 ADMIN
     */
    private boolean isTeacherOrAdminForCourse(Long courseId) {
        if (SecurityUtil.isAdmin()) {
            return true;
        }
        if (SecurityUtil.hasRole("TEACHER") && courseId != null) {
            Course course = courseRepository.selectById(courseId);
            if (course != null && SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
                return true;
            }
        }
        return false;
    }

    private List<DiscussionCommentVO> buildCommentTree(List<DiscussionComment> comments,
                                                        java.util.Map<Long, User> userMap,
                                                        Long opUserId) {
        if (comments == null || comments.isEmpty()) {
            return new ArrayList<>();
        }

        // 转换为VO并建立父子关系
        Map<Long, DiscussionCommentVO> voMap = new LinkedHashMap<>();
        for (DiscussionComment c : comments) {
            DiscussionCommentVO vo = new DiscussionCommentVO();
            vo.setId(c.getId());
            vo.setPostId(c.getPostId());
            vo.setParentId(c.getParentId());
            vo.setUserId(c.getUserId());
            vo.setContent(c.getContent());
            vo.setIsAnonymous(c.getIsAnonymous());
            vo.setIsTeacherReply(c.getIsTeacherReply());
            vo.setLikeCount(c.getLikeCount());
            vo.setCreatedAt(c.getCreatedAt());
            vo.setChildren(new ArrayList<>());

            // P0-1 + P1C-028: 匿名评论对教师/管理员可见真实身份
            if (Boolean.TRUE.equals(c.getIsAnonymous())) {
                DiscussionPost post = postRepository.selectById(c.getPostId());
                if (post != null && isTeacherOrAdminForCourse(post.getCourseId())) {
                    if (c.getUserId() != null) {
                        User author = userMap.get(c.getUserId());
                        if (author != null) {
                            vo.setAuthorName(author.getRealName() != null ? author.getRealName() : author.getUsername());
                            vo.setRoleTag(author.getRole() != null ? author.getRole().name() : null);
                        }
                    }
                } else {
                    vo.setAuthorName("匿名用户");
                    vo.setUserId(null);
                }
            } else if (c.getUserId() != null) {
                User author = userMap.get(c.getUserId());
                if (author != null) {
                    vo.setAuthorName(author.getRealName() != null ? author.getRealName() : author.getUsername());
                    vo.setRoleTag(author.getRole() != null ? author.getRole().name() : null);
                }
            }

            // isOp: 评论者是帖子作者
            if (opUserId != null && c.getUserId() != null && !Boolean.TRUE.equals(c.getIsAnonymous())) {
                vo.setIsOp(c.getUserId().equals(opUserId));
            } else {
                vo.setIsOp(false);
            }

            voMap.put(c.getId(), vo);
        }

        // 构建树
        List<DiscussionCommentVO> roots = new ArrayList<>();
        for (DiscussionCommentVO vo : voMap.values()) {
            if (vo.getParentId() == null) {
                roots.add(vo);
            } else {
                DiscussionCommentVO parent = voMap.get(vo.getParentId());
                if (parent != null) {
                    parent.getChildren().add(vo);
                } else {
                    // 父评论不存在，当作根节点
                    roots.add(vo);
                }
            }
        }

        return roots;
    }
}
