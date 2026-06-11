package com.microcourse.controller;

import com.microcourse.dto.LearningProgressVO;
import com.microcourse.dto.ProgressCreateRequest;
import com.microcourse.dto.ProgressUpdateRequest;
import com.microcourse.dto.R;
import com.microcourse.service.LearningProgressService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/learning-progress")
public class LearningProgressController {

    private final LearningProgressService learningProgressService;

    public LearningProgressController(LearningProgressService learningProgressService) {
        this.learningProgressService = learningProgressService;
    }

    @GetMapping("/progress")
    @PreAuthorize("isAuthenticated()")
    public R<List<LearningProgressVO>> getByUserAndCourse(
            @RequestParam Long userId,
            @RequestParam Long courseId) {
        List<LearningProgressVO> list = learningProgressService.getByUserAndCourse(userId, courseId);
        return R.ok(list);
    }

    @PostMapping("/progress")
    @PreAuthorize("isAuthenticated()")
    public R<LearningProgressVO> create(@Valid @RequestBody ProgressCreateRequest request) {
        LearningProgressVO vo = learningProgressService.create(request);
        return R.ok(vo);
    }

    @PutMapping("/progress/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<Void> updateProgress(@PathVariable Long id,
                                  @RequestBody ProgressUpdateRequest request) {
        Long userId = getCurrentUserId();
        learningProgressService.updateProgress(id, userId, request);
        return R.ok();
    }

    @GetMapping("/progress/completion")
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, Object>> getCourseCompletion(
            @RequestParam Long userId,
            @RequestParam Long courseId) {
        Map<String, Object> result = learningProgressService.getCourseCompletion(userId, courseId);
        return R.ok(result);
    }

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Long) return (Long) principal;
        return null;
    }
}