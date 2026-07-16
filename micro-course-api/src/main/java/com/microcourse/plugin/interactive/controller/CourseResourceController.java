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
@RequestMapping("/api/courses")
@ConditionalOnProperty(value = "plugin.interactive.enabled", havingValue = "true", matchIfMissing = true)
public class CourseResourceController {

    private final SectionResourceService resourceService;

    public CourseResourceController(SectionResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @PostMapping("/{courseId}/trainings")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<CourseTraining> createTraining(@PathVariable Long courseId,
                                            @Valid @RequestBody CreateTrainingRequest request) {
        return R.ok(resourceService.createTraining(courseId, request));
    }

    @PostMapping("/{courseId}/final-project")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<CourseFinalProject> createFinalProject(@PathVariable Long courseId,
                                                    @Valid @RequestBody CreateFinalProjectRequest request) {
        return R.ok(resourceService.createFinalProject(courseId, request));
    }
}
