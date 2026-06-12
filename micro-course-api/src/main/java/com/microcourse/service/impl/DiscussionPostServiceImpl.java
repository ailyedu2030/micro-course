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
import com.microcourse.entity.DiscussionPost;
import com.microcourse.entity.DiscussionComment;
import com.microcourse.entity.User;
import com.microcourse.enums.UserRole;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.DiscussionPostRepository;
import com.microcourse.repository.DiscussionCommentRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.service.DiscussionPostService;
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

    public DiscussionPostServiceImpl(DiscussionPostRepository postRepository,
                                     DiscussionCommentRepository commentRepository,
                                     UserRepository userRepository,
                                     CourseRepository courseRepository) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
    }

       @Override
    @Transactional(readOnly = true)
    public PageResult<DiscussionPostVO> page(Long chapterId, int page, int size) {
        LambdaQueryWrapper<DiscussionPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(chapterId != null, DiscussionPost::getChapterId, chapterId)
               .eq(DiscussionPost::getStatus, 1)
               .orderByDesc(DiscussionPost::getIsPinned)
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

        // keyword搜索：title或content模糊匹配
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.and(w -> w.like(DiscussionPost::getTitle, query.getKeyword())
                    .or().like(DiscussionPost::getContent, query.getKeyword()));
        }

        // 按courseId筛选
        wrapper.eq(query.getCourseId() != null, DiscussionPost::getCourseId, query.getCourseId());

        // 按status筛选（前端传字符串：PENDING/PUBLISHED/DELETED，数据库存整数）
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            Integer statusVal = switch (query.getStatus()) {
                case "PENDING" -> 0;
                case "PUBLISHED" -> 1;
                case "DELETED" -> 2;
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

        // status: Integer → String
        String statusStr = switch (post.getStatus()) {
            case 0 -> "PENDING";
            case 1 -> "PUBLISHED";
            case 2 -> "DELETED";
            default -> "PENDING";
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
        if (post == null || post.getStatus() == 0) {
            throw new BusinessException(ErrorCode.DISCUSSION_POST_NOT_FOUND);
        }

        // N+1 修复：批量预加载 post author 和 comment authors
        java.util.Map<Long, User> userMap = new java.util.HashMap<>();
        java.util.Set<Long> userIds = new java.util.HashSet<>();
        if (post.getUserId() != null) {
            userIds.add(post.getUserId());
        }

        // 查评论列表并构建树结构
        LambdaQueryWrapper<DiscussionComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DiscussionComment::getPostId, id)
               .eq(DiscussionComment::getStatus, 1)
               .orderByAsc(DiscussionComment::getCreatedAt);
        List<DiscussionComment> comments = commentRepository.selectList(wrapper);

        comments.forEach(c -> {
            if (c.getUserId() != null) userIds.add(c.getUserId());
        });

        if (!userIds.isEmpty()) {
            userRepository.selectBatchIds(userIds).forEach(u -> userMap.put(u.getId(), u));
        }
        final java.util.Map<Long, User> finalUserMap = userMap;

        DiscussionPostVO vo = convertToVO(post, finalUserMap);
        vo.setChildren(buildCommentTree(comments, finalUserMap));

        return vo;
    }

    @Override
    @Transactional
    public DiscussionPostVO create(PostCreateRequest req, Long userId) {
        DiscussionPost post = new DiscussionPost();
        post.setCourseId(req.getCourseId());
        post.setChapterId(req.getChapterId());
        post.setUserId(userId);
        post.setTitle(req.getTitle());
        post.setContent(req.getContent());
        post.setIsAnonymous(req.getIsAnonymous() != null ? req.getIsAnonymous() : false);
        post.setIsPinned(false);
        post.setIsEssence(false);
        post.setCommentCount(0);
        post.setLikeCount(0);
        post.setStatus(1);
        post.setCreatedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
        postRepository.insert(post);
        return convertToVO(post);
    }

    @Override
    @Transactional
    public DiscussionPostVO update(Long id, PostUpdateRequest request, Long userId) {
        DiscussionPost post = postRepository.selectById(id);
        if (post == null || post.getStatus() == 0) {
            throw new BusinessException(ErrorCode.DISCUSSION_POST_NOT_FOUND);
        }
        // 检查是否是作者或管理员
        User user = userRepository.selectById(userId);
        boolean isAdmin = user != null && user.getRole() == UserRole.ADMIN;
        if (!post.getUserId().equals(userId) && !isAdmin) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        if (request.getTitle() != null) {
            post.setTitle(request.getTitle());
        }
        if (request.getContent() != null) {
            post.setContent(request.getContent());
        }
        if (request.getIsAnonymous() != null) {
            post.setIsAnonymous(request.getIsAnonymous());
        }
        post.setUpdatedAt(LocalDateTime.now());
        postRepository.updateById(post);
        return convertToVO(post);
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        DiscussionPost post = postRepository.selectById(id);
        if (post == null || post.getStatus() == 0) {
            throw new BusinessException(ErrorCode.DISCUSSION_POST_NOT_FOUND);
        }
        // 检查是否是作者或管理员
        User user = userRepository.selectById(userId);
        boolean isAdmin = user != null && user.getRole() == UserRole.ADMIN;
        if (!post.getUserId().equals(userId) && !isAdmin) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        // 软删：status=0
        post.setStatus(0);
        post.setUpdatedAt(LocalDateTime.now());
        postRepository.updateById(post);
    }

    @Override
    @Transactional
    public void pin(Long id) {
        DiscussionPost post = postRepository.selectById(id);
        if (post == null || post.getStatus() == 0) {
            throw new BusinessException(ErrorCode.DISCUSSION_POST_NOT_FOUND);
        }
        // is_pinned = !is_pinned (toggle)
        post.setIsPinned(!post.getIsPinned());
        post.setUpdatedAt(LocalDateTime.now());
        postRepository.updateById(post);
    }

    @Override
    @Transactional
    public void updatePin(Long id, boolean pinned) {
        DiscussionPost post = postRepository.selectById(id);
        if (post == null || post.getStatus() == 0) {
            throw new BusinessException(ErrorCode.DISCUSSION_POST_NOT_FOUND);
        }
        post.setIsPinned(pinned);
        post.setUpdatedAt(LocalDateTime.now());
        postRepository.updateById(post);
    }

    @Override
    @Transactional
    public void updateEssence(Long id, boolean essence) {
        DiscussionPost post = postRepository.selectById(id);
        if (post == null || post.getStatus() == 0) {
            throw new BusinessException(ErrorCode.DISCUSSION_POST_NOT_FOUND);
        }
        post.setIsEssence(essence);
        post.setUpdatedAt(LocalDateTime.now());
        postRepository.updateById(post);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, String status) {
        DiscussionPost post = postRepository.selectById(id);
        if (post == null) {
            throw new BusinessException(ErrorCode.DISCUSSION_POST_NOT_FOUND);
        }
        Integer statusVal = switch (status) {
            case "APPROVED" -> 1;   // PUBLISHED
            case "REJECTED" -> 2;   // DELETED
            case "PENDING"  -> 0;
            case "PUBLISHED" -> 1;
            case "DELETED"  -> 2;
            default -> throw new BusinessException(ErrorCode.DISCUSSION_POST_NOT_FOUND);
        };
        post.setStatus(statusVal);
        post.setUpdatedAt(LocalDateTime.now());
        postRepository.updateById(post);
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

    private List<DiscussionCommentVO> buildCommentTree(List<DiscussionComment> comments,
                                                        java.util.Map<Long, User> userMap) {
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
            vo.setIsTeacherReply(c.getIsTeacherReply());
            vo.setLikeCount(c.getLikeCount());
            vo.setCreatedAt(c.getCreatedAt());
            vo.setChildren(new ArrayList<>());

            // 查作者名（使用预加载的 Map）
            if (c.getUserId() != null) {
                User author = userMap.get(c.getUserId());
                if (author != null) {
                    vo.setAuthorName(author.getRealName() != null ? author.getRealName() : author.getUsername());
                }
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
