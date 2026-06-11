package com.microcourse.controller;

import com.microcourse.dto.DiscussionPostVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.PostCreateRequest;
import com.microcourse.dto.PostUpdateRequest;
import com.microcourse.dto.R;
import com.microcourse.service.DiscussionPostService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/discussions/posts")
public class DiscussionPostController {

    private final DiscussionPostService postService;

    public DiscussionPostController(DiscussionPostService postService) {
        this.postService = postService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<PageResult<DiscussionPostVO>> page(
            @RequestParam(required = false) Long chapterId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResult<DiscussionPostVO> result = postService.page(chapterId, page, size);
        return R.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<DiscussionPostVO> getById(@PathVariable Long id) {
        DiscussionPostVO vo = postService.getById(id);
        return R.ok(vo);
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public R<DiscussionPostVO> create(@Valid @RequestBody PostCreateRequest request) {
        Long userId = getCurrentUserId();
        DiscussionPostVO vo = postService.create(request, userId);
        return R.ok(vo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<DiscussionPostVO> update(@PathVariable Long id, @Valid @RequestBody PostUpdateRequest request) {
        Long userId = getCurrentUserId();
        DiscussionPostVO vo = postService.update(id, request, userId);
        return R.ok(vo);
    }

    @PutMapping("/{id}/pin")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> pin(@PathVariable Long id) {
        postService.pin(id);
        return R.ok();
    }

    @PutMapping("/{id}/essence")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> essence(@PathVariable Long id, @RequestParam boolean essence) {
        postService.updateEssence(id, essence);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<Void> delete(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        postService.delete(id, userId);
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
