package com.microcourse.controller;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.dto.bundle.BundleCreateRequest;
import com.microcourse.dto.bundle.BundleVO;
import com.microcourse.service.CourseBundleService;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/course-bundles")
@Validated
public class CourseBundleController {

    private final CourseBundleService bundleService;

    public CourseBundleController(CourseBundleService bundleService) {
        this.bundleService = bundleService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<PageResult<BundleVO>> page(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 10000) int size) {
        return R.ok(bundleService.page(page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<BundleVO> getById(@PathVariable Long id) {
        return R.ok(bundleService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<BundleVO> create(@RequestBody BundleCreateRequest request) {
        return R.ok(bundleService.create(request));
    }

    @PostMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> addCourse(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long courseId = body.get("courseId") instanceof Number ? ((Number) body.get("courseId")).longValue() : null;
        Integer sortOrder = body.get("sortOrder") instanceof Number ? ((Number) body.get("sortOrder")).intValue() : 0;
        Boolean isRequired = body.get("isRequired") instanceof Boolean ? (Boolean) body.get("isRequired") : true;
        bundleService.addCourse(id, courseId, sortOrder, isRequired);
        return R.ok();
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> removeCourse(@PathVariable Long id, @PathVariable Long itemId) {
        bundleService.removeCourse(id, itemId);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public R<Void> delete(@PathVariable Long id) {
        bundleService.delete(id);
        return R.ok();
    }
}
