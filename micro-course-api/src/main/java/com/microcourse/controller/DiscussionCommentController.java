package com.microcourse.controller;

import com.microcourse.constants.ApiLimits;
import com.microcourse.dto.CommentCreateRequest;
import com.microcourse.dto.DiscussionCommentVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.service.DiscussionCommentService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/discussions")
public class DiscussionCommentController {

    private final DiscussionCommentService commentService;

    public DiscussionCommentController(DiscussionCommentService commentService) {
        this.commentService = commentService;
    }

    /**
     * 获取讨论帖评论。
     *
     * <p>P1-I-2026-08-15 · 修复"返回全量 List" DoS 风险：
     * <ul>
     *   <li>若调用方传 {@code size} 参数（>=0）→ 返回 {@link PageResult}（分页契约）</li>
     *   <li>若调用方未传 {@code size}（兼容老前端） → 仍返回 {@link List}，但 Service 硬限 100 条</li>
     * </ul>
     */
    @GetMapping("/comments")
    @PreAuthorize("isAuthenticated()")
    public R<?> page(@RequestParam Long postId,
                     @RequestParam(required = false) Integer page,
                     @RequestParam(required = false) Integer size) {
        // 兼容老调用（无 page/size）→ 返回 List（受限 100）
        if (page == null && size == null) {
            List<DiscussionCommentVO> list = commentService.page(postId);
            // 硬限保护（Service 也加了，这里是双层防御）
            if (list.size() > ApiLimits.MAX_REQUEST_SIZE) {
                return R.ok(new java.util.ArrayList<>(list.subList(0, ApiLimits.MAX_REQUEST_SIZE)));
            }
            return R.ok(list);
        }
        // 新调用（带 page/size）→ 返回 PageResult
        int p = page == null ? 0 : page;
        int s = size == null ? 20 : Math.min(Math.max(size, 1), ApiLimits.MAX_REQUEST_SIZE);
        PageResult<DiscussionCommentVO> result = commentService.pagePaged(postId, p, s);
        return R.ok(result);
    }

    @PostMapping("/comments")
    @PreAuthorize("isAuthenticated()")
    public R<DiscussionCommentVO> create(@Valid @RequestBody CommentCreateRequest request) {
        Long userId = getCurrentUserId();
        DiscussionCommentVO vo = commentService.create(request, userId);
        return R.ok(vo);
    }

    // P0-3 对象级授权：@PreAuthorize 仅做认证兜底；owner 校验下沉至
    // DiscussionCommentServiceImpl.delete（!comment.userId.equals(userId) && !isAdminOrTeacher → 拒绝）
    @DeleteMapping("/comments/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<Void> delete(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        commentService.delete(id, userId);
        return R.ok();
    }

    @PostMapping("/comments/{id}/like")
    @PreAuthorize("isAuthenticated()")
    public R<Void> like(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        commentService.like(id, userId);
        return R.ok();
    }

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }
        throw new com.microcourse.exception.BusinessException(com.microcourse.exception.ErrorCode.TOKEN_INVALID);
    }
}