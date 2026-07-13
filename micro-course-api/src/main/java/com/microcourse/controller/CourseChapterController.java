package com.microcourse.controller;

import com.microcourse.audit.AuditedLog;
import com.microcourse.dto.ChapterCreateRequest;
import com.microcourse.dto.ChapterSortRequest;
import com.microcourse.dto.ChapterUpdateRequest;
import com.microcourse.dto.ChapterVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.service.CourseChapterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

@RestController
@RequestMapping("/api/chapters")
@Tag(name = "CourseChapterController", description = "CourseChapterController 自动生成 OpenAPI 文档")
public class CourseChapterController {

    private static final Logger log = LoggerFactory.getLogger(CourseChapterController.class);

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
        if (courseId == null) {
            throw new com.microcourse.exception.BusinessException(com.microcourse.exception.ErrorCode.BAD_REQUEST_PARAM, "courseId 不能为空");
        }
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
        log.info("[ChapterUpdate] id={} title='{}'", id, request.getTitle());
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