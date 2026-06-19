package com.microcourse.controller;

import com.microcourse.dto.DiscussionPageQuery;
import com.microcourse.dto.DiscussionPostVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.service.DiscussionPostService;
import com.microcourse.util.SecurityUtil;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/discussions")
public class DiscussionAdminController {

    private final DiscussionPostService postService;

    public DiscussionAdminController(DiscussionPostService postService) {
        this.postService = postService;
    }

    /**
     * GET /api/discussions
     * 管理后台讨论列表（支持 keyword/courseId/status 分页）
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<PageResult<DiscussionPostVO>> page(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "10") @Range(min = 1, max = 200) int size,
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
     */
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<Void> approve(@PathVariable Long id) {
        postService.updateStatus(id, "APPROVED");
        return R.ok();
    }

    /**
     * PUT /api/discussions/{id}/reject
     * 审核驳回
     */
    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<Void> reject(@PathVariable Long id) {
        postService.updateStatus(id, "REJECTED");
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
}