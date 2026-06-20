package com.microcourse.controller;

import com.microcourse.dto.R;
import com.microcourse.dto.lesson.LessonVO;
import com.microcourse.service.LessonService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/lessons")
public class LessonController {

    private final LessonService lessonService;

    public LessonController(LessonService lessonService) {
        this.lessonService = lessonService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<LessonVO> create(@RequestBody Map<String, Object> body) {
        Long chapterId = body.get("chapterId") instanceof Number ? ((Number) body.get("chapterId")).longValue() : null;
        Long courseId = body.get("courseId") instanceof Number ? ((Number) body.get("courseId")).longValue() : null;
        String title = (String) body.getOrDefault("title", "新课时");
        String lessonType = (String) body.getOrDefault("lessonType", "VIDEO");
        return R.ok(lessonService.create(chapterId, courseId, title, lessonType));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<LessonVO> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String title = (String) body.get("title");
        Integer duration = body.get("duration") instanceof Number ? ((Number) body.get("duration")).intValue() : null;
        Boolean visible = body.get("visible") instanceof Boolean ? (Boolean) body.get("visible") : null;
        return R.ok(lessonService.update(id, title, duration, visible));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> delete(@PathVariable Long id) {
        lessonService.delete(id);
        return R.ok();
    }

    @PutMapping("/sort")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> sort(@RequestBody List<LessonVO.SortItem> items) {
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
