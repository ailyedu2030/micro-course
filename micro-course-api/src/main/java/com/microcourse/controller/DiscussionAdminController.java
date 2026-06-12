package com.microcourse.controller;

import com.microcourse.dto.DiscussionPageQuery;
import com.microcourse.dto.DiscussionPostVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.service.DiscussionPostService;
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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
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
}