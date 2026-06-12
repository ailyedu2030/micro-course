package com.microcourse.controller;

import com.microcourse.dto.*;
import com.microcourse.service.GradeService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/grades")
public class GradeController {

    private final GradeService gradeService;

    public GradeController(GradeService gradeService) {
        this.gradeService = gradeService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<PageResult<GradeVO>> page(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long studentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(gradeService.page(courseId, studentId, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<GradeVO> getById(@PathVariable Long id) {
        return R.ok(gradeService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<GradeVO> create(
            @Valid @RequestBody GradeCreateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long teacherId = getCurrentUserId(userDetails);
        return R.ok(gradeService.create(request, teacherId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<GradeVO> update(
            @PathVariable Long id,
            @Valid @RequestBody GradeUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long teacherId = getCurrentUserId(userDetails);
        return R.ok(gradeService.update(id, request, teacherId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> delete(@PathVariable Long id) {
        gradeService.delete(id);
        return R.ok();
    }

    private Long getCurrentUserId(UserDetails userDetails) {
        if (userDetails instanceof com.microcourse.entity.User) {
            return ((com.microcourse.entity.User) userDetails).getId();
        }
        try {
            return Long.parseLong(userDetails.getUsername());
        } catch (NumberFormatException e) {
            return 1L;
        }
    }
}