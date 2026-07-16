package com.microcourse.plugin.interactive.controller;

import com.microcourse.dto.R;
import com.microcourse.plugin.interactive.dto.*;
import com.microcourse.plugin.interactive.entity.*;
import com.microcourse.plugin.interactive.service.SectionResourceService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses/{courseId}/sections/{sectionId}")
@ConditionalOnProperty(value = "plugin.interactive.enabled", havingValue = "true", matchIfMissing = true)
public class SectionResourceController {

    private final SectionResourceService resourceService;

    public SectionResourceController(SectionResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @PostMapping("/quizzes")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<QuizVO> createQuiz(@PathVariable Long courseId,
                                @PathVariable Long sectionId,
                                @Valid @RequestBody CreateQuizRequest request) {
        return R.ok(resourceService.createQuiz(courseId, sectionId, request));
    }

    @PostMapping("/tasks")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<SectionTask> createTask(@PathVariable Long courseId,
                                     @PathVariable Long sectionId,
                                     @Valid @RequestBody CreateTaskRequest request) {
        return R.ok(resourceService.createTask(courseId, sectionId, request));
    }

    @PostMapping("/reflections")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<SectionReflection> createReflection(@PathVariable Long courseId,
                                                   @PathVariable Long sectionId,
                                                   @Valid @RequestBody CreateReflectionRequest request) {
        return R.ok(resourceService.createReflection(courseId, sectionId, request));
    }
}
