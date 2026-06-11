package com.microcourse.controller;

import com.microcourse.dto.*;
import com.microcourse.service.CourseChapterService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/chapters")
public class CourseChapterController {

    private final CourseChapterService chapterService;

    public CourseChapterController(CourseChapterService chapterService) {
        this.chapterService = chapterService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<PageResult<ChapterVO>> page(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long courseId) {
        PageResult<ChapterVO> result = chapterService.page(page, size, courseId);
        return R.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<ChapterVO> getById(@PathVariable Long id) {
        ChapterVO vo = chapterService.getById(id);
        return R.ok(vo);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<ChapterVO> create(@Valid @RequestBody ChapterCreateRequest request) {
        ChapterVO vo = chapterService.create(request);
        return R.ok(vo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<ChapterVO> update(@PathVariable Long id,
                               @Valid @RequestBody ChapterUpdateRequest request) {
        ChapterVO vo = chapterService.update(id, request);
        return R.ok(vo);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> delete(@PathVariable Long id) {
        chapterService.delete(id);
        return R.ok();
    }
}