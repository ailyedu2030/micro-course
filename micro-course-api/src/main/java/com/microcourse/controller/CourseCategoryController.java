package com.microcourse.controller;

import com.microcourse.dto.CourseCategoryCreateRequest;
import com.microcourse.dto.CourseCategoryUpdateRequest;
import com.microcourse.dto.CourseCategoryVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.service.CourseCategoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/course-categories")
public class CourseCategoryController {

    private final CourseCategoryService courseCategoryService;

    public CourseCategoryController(CourseCategoryService courseCategoryService) {
        this.courseCategoryService = courseCategoryService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<PageResult<CourseCategoryVO>> page(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 1000) int size) {
        PageResult<CourseCategoryVO> result = courseCategoryService.page(page, size);
        return R.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<CourseCategoryVO> getById(@PathVariable Long id) {
        CourseCategoryVO vo = courseCategoryService.getById(id);
        return R.ok(vo);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public R<CourseCategoryVO> create(@Valid @RequestBody CourseCategoryCreateRequest request) {
        CourseCategoryVO vo = courseCategoryService.create(request);
        return R.ok(vo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public R<CourseCategoryVO> update(@PathVariable Long id,
                                     @Valid @RequestBody CourseCategoryUpdateRequest request) {
        CourseCategoryVO vo = courseCategoryService.update(id, request);
        return R.ok(vo);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public R<Void> delete(@PathVariable Long id) {
        courseCategoryService.delete(id);
        return R.ok();
    }
}