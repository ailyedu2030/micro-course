package com.microcourse.controller;

import com.microcourse.dto.*;
import com.microcourse.service.TeachingClassService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teaching-classes")
public class TeachingClassController {

    private final TeachingClassService teachingClassService;

    public TeachingClassController(TeachingClassService teachingClassService) {
        this.teachingClassService = teachingClassService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<PageResult<TeachingClassVO>> page(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) Integer status) {
        PageResult<TeachingClassVO> result = teachingClassService.page(page, size, teacherId, courseId, semester, status);
        return R.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<TeachingClassVO> getById(@PathVariable Long id) {
        TeachingClassVO vo = teachingClassService.getById(id);
        return R.ok(vo);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public R<TeachingClassVO> create(@Valid @RequestBody TeachingClassCreateRequest request) {
        TeachingClassVO vo = teachingClassService.create(request);
        return R.ok(vo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ACADEMIC')")
    public R<TeachingClassVO> update(@PathVariable Long id,
                                     @Valid @RequestBody TeachingClassUpdateRequest request) {
        TeachingClassVO vo = teachingClassService.update(id, request);
        return R.ok(vo);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<Void> delete(@PathVariable Long id) {
        teachingClassService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}/students")
    @PreAuthorize("isAuthenticated()")
    public R<List<TeachingClassStudentVO>> getClassStudents(@PathVariable Long id) {
        List<TeachingClassStudentVO> list = teachingClassService.getClassStudents(id);
        return R.ok(list);
    }

    @PostMapping("/{id}/students")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public R<Void> addStudent(@PathVariable Long id, @RequestParam Long userId) {
        teachingClassService.addStudent(id, userId);
        return R.ok();
    }

    @DeleteMapping("/{id}/students/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ACADEMIC')")
    public R<Void> removeStudent(@PathVariable Long id, @PathVariable Long userId) {
        teachingClassService.removeStudent(id, userId);
        return R.ok();
    }

    @PutMapping("/{id}/students/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ACADEMIC')")
    public R<Void> updateStudentStatus(@PathVariable Long id,
                                      @PathVariable Long userId,
                                      @RequestParam String status) {
        teachingClassService.updateStudentStatus(id, userId, status);
        return R.ok();
    }
}