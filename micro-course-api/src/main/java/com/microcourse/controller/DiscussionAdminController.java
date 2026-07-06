package com.microcourse.controller;

import com.microcourse.dto.DiscussionCommentVO;
import com.microcourse.dto.DiscussionPageQuery;
import com.microcourse.dto.DiscussionPostVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.service.DiscussionCommentService;
import com.microcourse.service.DiscussionPostService;
import com.microcourse.util.SecurityUtil;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/discussions")
public class DiscussionAdminController {

    private final DiscussionPostService postService;
    private final DiscussionCommentService commentService;

    public DiscussionAdminController(DiscussionPostService postService,
                                     DiscussionCommentService commentService) {
        this.postService = postService;
        this.commentService = commentService;
    }

    /**
     * GET /api/discussions
     * 管理后台讨论列表（支持 keyword/courseId/status 分页）
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<PageResult<DiscussionPostVO>> page(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "10") @Range(min = 1, max = 100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String status) {
        DiscussionPageQuery query = new DiscussionPageQuery();
        query.setPage(page);
        query.setSize(size);
        query.setKeyword(keyword);
        query.setCourseId(courseId);
        query.setStatus(status);
        PageResult<DiscussionPostVO> result = postService.pageAdmin(query);
        return R.ok(result);
    }

    /**
     * GET /api/discussions/{id}
     * 讨论详情（管理端）
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<DiscussionPostVO> getById(@PathVariable Long id) {
        DiscussionPostVO vo = postService.getById(id);
        return R.ok(vo);
    }

    /**
     * PUT /api/discussions/{id}/approve
     * 审核通过
     * 【根因】I-1: @PreAuthorize 只有 ADMIN/ACADEMIC，TEACHER 被排除，但教师应可审核自己课程的帖子
     * 【修复】添加 TEACHER 角色
     * 【防止再发】审核相关端点统一对 TEACHER 开放
     */
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC','TEACHER')")
    public R<Void> approve(@PathVariable Long id) {
        postService.updateStatus(id, "APPROVED");
        return R.ok();
    }

    /**
     * PUT /api/discussions/{id}/reject
     * 审核驳回（P1C-060: 必填驳回原因）
     * 【根因】I-1: @PreAuthorize 只有 ADMIN/ACADEMIC，TEACHER 被排除
     * 【修复】添加 TEACHER 角色
     * 【防止再发】审核相关端点统一对 TEACHER 开放
     */
    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC','TEACHER')")
    public R<Void> reject(@PathVariable Long id,
                          @RequestParam @NotBlank(message = "驳回原因不能为空") String reason) {
        postService.rejectWithReason(id, reason);
        return R.ok();
    }

    /**
     * DELETE /api/discussions/{id}
     * 删除讨论（管理端）
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<Void> delete(@PathVariable Long id) {
        // DISC-NEW-1 修复:传入真实 userId,避免 Service 层 userId=null 导致权限校验失败
        postService.delete(id, SecurityUtil.getCurrentUserId());
        return R.ok();
    }

    // ========== P2-6 修复: 评论管理端点 ==========

    /**
     * GET /api/admin/discussions/comments
     * 评论列表（管理端，支持 keyword/postId 筛选）
     */
    @GetMapping("/comments")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<PageResult<DiscussionCommentVO>> commentPage(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "10") @Range(min = 1, max = 100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long postId) {
        return R.ok(commentService.pageAdmin(page, size, keyword, postId));
    }

    /**
     * DELETE /api/admin/discussions/comments/{id}
     * 删除评论（管理端）
     */
    @DeleteMapping("/comments/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<Void> deleteComment(@PathVariable Long id) {
        commentService.deleteByAdmin(id);
        return R.ok();
    }

    /**
     * PUT /api/admin/discussions/comments/{id}/pin
     * 置顶/取消置顶评论
     */
    @PutMapping("/comments/{id}/pin")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<Void> pinComment(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        boolean pinned = body.getOrDefault("pinned", false);
        commentService.pinComment(id, pinned);
        return R.ok();
    }
}