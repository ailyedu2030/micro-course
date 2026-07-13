package com.microcourse.controller;

import com.microcourse.dto.*;
import com.microcourse.service.SectionService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses/{courseId}/chapters/{chapterId}/sections")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class SectionController {
    private final SectionService sectionService;

    public SectionController(SectionService sectionService) {
        this.sectionService = sectionService;
    }

    @GetMapping
    public R<PageResult<SectionDTO>> list(
            @PathVariable Long chapterId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(sectionService.listByChapter(chapterId, page, size));
    }

    @GetMapping("/{id}")
    public R<SectionDTO> getById(@PathVariable Long id) {
        return R.ok(sectionService.getById(id));
    }

    @PostMapping
    public R<SectionDTO> create(
            @PathVariable Long chapterId,
            @Valid @RequestBody SectionCreateRequest request) {
        return R.ok(sectionService.create(chapterId, request));
    }

    @PutMapping("/{id}")
    public R<SectionDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody SectionUpdateRequest request) {
        return R.ok(sectionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean force) {
        sectionService.delete(id, force);
        return R.ok();
    }
}
