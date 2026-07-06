package com.microcourse.controller;

import com.microcourse.dto.R;
import com.microcourse.dto.lesson.LessonCreateRequest;
import com.microcourse.dto.lesson.LessonUpdateRequest;
import com.microcourse.dto.lesson.LessonVO;
import com.microcourse.service.LessonService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@RestController
@RequestMapping("/api/lessons")
@Tag(name = "LessonController", description = "LessonController 自动生成 OpenAPI 文档")
public class LessonController {

    private final LessonService lessonService;

    public LessonController(LessonService lessonService) {
        this.lessonService = lessonService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<LessonVO> create(@Valid @RequestBody LessonCreateRequest req) {
        return R.ok(lessonService.create(req.getChapterId(), req.getCourseId(), req.getTitle(), req.getLessonType()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<LessonVO> update(@PathVariable Long id, @Valid @RequestBody LessonUpdateRequest req) {
        return R.ok(lessonService.update(id, req.getTitle(), req.getDuration(), req.getVisible()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> delete(@PathVariable Long id) {
        lessonService.delete(id);
        return R.ok();
    }

    @PutMapping("/sort")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> sort(@RequestBody @Valid List<LessonVO.SortItem> items) {
        lessonService.sort(items);
        return R.ok();
    }

    @GetMapping("/chapter/{chapterId}")
    @PreAuthorize("isAuthenticated()")
    public R<List<LessonVO>> getByChapter(@PathVariable Long chapterId) {
        return R.ok(lessonService.getByChapter(chapterId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<LessonVO> getById(@PathVariable Long id) {
        return R.ok(lessonService.getById(id));
    }
}
