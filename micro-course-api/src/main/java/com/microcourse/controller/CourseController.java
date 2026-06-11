package com.microcourse.controller;

import com.microcourse.dto.*;
import com.microcourse.service.CourseService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<PageResult<CourseVO>> page(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) Integer status) {
        CoursePageQuery query = new CoursePageQuery();
        query.setPage(page);
        query.setSize(size);
        query.setTitle(title);
        query.setCategoryId(categoryId);
        query.setTeacherId(teacherId);
        query.setStatus(status);
        PageResult<CourseVO> result = courseService.page(query);
        return R.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<CourseVO> getById(@PathVariable Long id) {
        CourseVO vo = courseService.getById(id);
        return R.ok(vo);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<CourseVO> create(@Valid @RequestBody CourseCreateRequest request) {
        CourseVO vo = courseService.create(request);
        return R.ok(vo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<CourseVO> update(@PathVariable Long id,
                              @Valid @RequestBody CourseUpdateRequest request) {
        CourseVO vo = courseService.update(id, request);
        return R.ok(vo);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public R<Void> updateStatus(@PathVariable Long id,
                                @RequestParam Integer status) {
        courseService.updateStatus(id, status);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public R<Void> delete(@PathVariable Long id) {
        courseService.delete(id);
        return R.ok();
    }
}