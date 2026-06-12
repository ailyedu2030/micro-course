package com.microcourse.controller;

import com.microcourse.dto.*;
import com.microcourse.service.CourseService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Boolean recommended) {
        CoursePageQuery query = new CoursePageQuery();
        query.setPage(page);
        query.setSize(size);
        query.setTitle(title);
        query.setKeyword(keyword);
        query.setCategoryId(categoryId);
        query.setTeacherId(teacherId);
        query.setStatus(status);
        query.setRecommended(recommended);
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

    /**
     * POST /api/courses/{id}/submit
     * 提交课程审核（草稿 → 待审核）
     * 权限：TEACHER, ADMIN
     */
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> submitForReview(@PathVariable Long id) {
        courseService.submitForReview(id);
        return R.ok();
    }

    /**
     * POST /api/courses/{id}/approve
     * 审核通过（待审核 → 已通过）
     * 权限：ADMIN
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public R<Void> approve(@PathVariable Long id) {
        courseService.approve(id);
        return R.ok();
    }

    /**
     * POST /api/courses/{id}/reject
     * 审核拒绝（待审核 → 已驳回）
     * 权限：ADMIN
     * @param body {"reason": "拒绝原因"}
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public R<Void> reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String reason = body.getOrDefault("reason", "");
        courseService.reject(id, reason);
        return R.ok();
    }

    /**
     * POST /api/courses/{id}/publish
     * 发布课程（已通过 → 已发布）
     * 权限：TEACHER, ADMIN
     */
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public R<Void> publish(@PathVariable Long id) {
        courseService.publish(id);
        return R.ok();
    }

    /**
     * POST /api/courses/{id}/copy
     * 复制课程（模板复制：复制课程基本信息 + 章节结构，不含视频文件）
     * 权限：TEACHER（课程创建者）, ADMIN
     * @return 新课程VO
     */
    @PostMapping("/{id}/copy")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<CourseVO> copy(@PathVariable Long id) {
        CourseVO vo = courseService.copy(id);
        return R.ok(vo);
    }
}