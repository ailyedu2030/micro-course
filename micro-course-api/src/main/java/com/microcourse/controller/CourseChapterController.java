package com.microcourse.controller;

import com.microcourse.audit.AuditedLog;
import com.microcourse.dto.ChapterCreateRequest;
import com.microcourse.dto.ChapterSortRequest;
import com.microcourse.dto.ChapterUpdateRequest;
import com.microcourse.dto.ChapterVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.service.CourseChapterService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import java.util.List;

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
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 10000) int size,
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
    @AuditedLog("创建章节")
    public R<ChapterVO> create(@Valid @RequestBody ChapterCreateRequest request) {
        ChapterVO vo = chapterService.create(request);
        return R.ok(vo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("更新章节")
    public R<ChapterVO> update(@PathVariable Long id,
                               @Valid @RequestBody ChapterUpdateRequest request) {
        ChapterVO vo = chapterService.update(id, request);
        return R.ok(vo);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("删除章节")
    public R<Void> delete(@PathVariable Long id) {
        chapterService.delete(id);
        return R.ok();
    }

    @PutMapping("/sort")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("章节排序")
    public R<Void> sort(@Valid @RequestBody List<ChapterSortRequest> requests) {
        chapterService.sort(requests);
        return R.ok();
    }
}