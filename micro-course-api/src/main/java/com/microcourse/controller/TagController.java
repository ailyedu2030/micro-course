package com.microcourse.controller;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.dto.TagCreateRequest;
import com.microcourse.dto.TagVO;
import com.microcourse.service.TagService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<PageResult<TagVO>> page(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResult<TagVO> result = tagService.page(page, size);
        return R.ok(result);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public R<TagVO> create(@Valid @RequestBody TagCreateRequest request) {
        TagVO vo = tagService.create(request);
        return R.ok(vo);
    }
}