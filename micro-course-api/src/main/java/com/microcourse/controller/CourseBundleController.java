package com.microcourse.controller;

import com.microcourse.audit.AuditedLog;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.dto.bundle.AddCourseRequest;
import com.microcourse.dto.bundle.BundleCreateRequest;
import com.microcourse.dto.bundle.BundleUpdateRequest;
import com.microcourse.dto.bundle.BundleVO;
import com.microcourse.service.CourseBundleService;
import com.microcourse.util.SecurityUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
    @AuditedLog("创建课程套餐")
    public R<BundleVO> create(@Valid @RequestBody BundleCreateRequest request) {
        return R.ok(bundleService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("编辑课程套餐")
    public R<BundleVO> update(@PathVariable Long id, @Valid @RequestBody BundleUpdateRequest request) {
        return R.ok(bundleService.update(id, request));
    }

    @PatchMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("上架课程套餐")
    public R<Void> publish(@PathVariable Long id) {
        bundleService.publish(id);
        return R.ok();
    }

    @PatchMapping("/{id}/unpublish")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("下架课程套餐")
    public R<Void> unpublish(@PathVariable Long id) {
        bundleService.unpublish(id);
        return R.ok();
    }

    @PostMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("向套餐添加课程")
    public R<Void> addCourse(@PathVariable Long id, @Valid @RequestBody AddCourseRequest request) {
        bundleService.addCourse(id, request.getCourseId(), request.getSortOrder(), request.getIsRequired());
        return R.ok();
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("从套餐移除课程")
    public R<Void> removeCourse(@PathVariable Long id, @PathVariable Long itemId) {
        bundleService.removeCourse(id, itemId);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @AuditedLog("删除课程套餐")
    public R<Void> delete(@PathVariable Long id) {
        bundleService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}/my-enrollment")
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, Object>> getMyEnrollmentStatus(@PathVariable Long id) {
        boolean enrolled = bundleService.isUserEnrolledInBundle(SecurityUtil.getCurrentUserId(), id);
        return R.ok(Map.of("enrolled", enrolled));
    }
}
