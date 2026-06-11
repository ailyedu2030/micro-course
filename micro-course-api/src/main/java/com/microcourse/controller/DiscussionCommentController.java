package com.microcourse.controller;

import com.microcourse.dto.CommentCreateRequest;
import com.microcourse.dto.DiscussionCommentVO;
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

    @GetMapping("/comments")
    @PreAuthorize("isAuthenticated()")
    public R<List<DiscussionCommentVO>> page(@RequestParam Long postId) {
        List<DiscussionCommentVO> list = commentService.page(postId);
        return R.ok(list);
    }

    @PostMapping("/comments")
    @PreAuthorize("isAuthenticated()")
    public R<DiscussionCommentVO> create(@Valid @RequestBody CommentCreateRequest request) {
        Long userId = getCurrentUserId();
        DiscussionCommentVO vo = commentService.create(request, userId);
        return R.ok(vo);
    }

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
        commentService.like(id);
        return R.ok();
    }

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }
        return null;
    }
}