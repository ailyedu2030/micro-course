package com.microcourse.controller;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.dto.TagCreateRequest;
import com.microcourse.dto.TagUpdateRequest;
import com.microcourse.dto.TagVO;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.TagRepository;
import com.microcourse.service.TagService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagService tagService;
    private final TagRepository tagRepository;

    public TagController(TagService tagService, TagRepository tagRepository) {
        this.tagService = tagService;
        this.tagRepository = tagRepository;
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

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody TagUpdateRequest request) {
        var tag = tagRepository.selectById(id);
        if (tag == null) {
            throw new BusinessException(ErrorCode.TAG_NOT_FOUND);
        }
        tag.setName(request.getName());
        tagRepository.updateById(tag);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<Void> delete(@PathVariable Long id) {
        tagRepository.deleteById(id);
        return R.ok();
    }
}