package com.microcourse.controller;

import com.microcourse.audit.AuditedLog;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.dto.VideoCreateRequest;
import com.microcourse.dto.VideoStatusVO;
import com.microcourse.dto.VideoUpdateRequest;
import com.microcourse.dto.VideoVO;
import com.microcourse.service.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.hibernate.validator.constraints.Range;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/courses/{courseId}/videos")
@Tag(name = "课程视频管理", description = "视频管理课程子资源路径（与/api/videos平行，保留向后兼容）")
public class CourseVideoController {

    private final VideoService videoService;

    public CourseVideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "获取课程下视频列表")
    public R<PageResult<VideoVO>> listByCourse(
            @PathVariable Long courseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 10000) int size) {
        return R.ok(videoService.page(courseId, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "获取视频详情")
    public R<VideoVO> getById(@PathVariable Long courseId, @PathVariable Long id) {
        videoService.assertCourseOwnership(courseId);
        return R.ok(videoService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("创建视频")
    @Operation(summary = "在课程下创建视频")
    public R<VideoVO> create(@PathVariable Long courseId,
                             @Valid @RequestBody VideoCreateRequest request) {
        if (request.getCourseId() == null) {
            request.setCourseId(courseId);
        }
        videoService.assertCourseOwnership(courseId);
        VideoVO vo = videoService.create(request);
        return R.ok(vo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("更新视频")
    @Operation(summary = "更新视频")
    public R<VideoVO> update(@PathVariable Long courseId,
                             @PathVariable Long id,
                             @Valid @RequestBody VideoUpdateRequest request) {
        videoService.assertCourseOwnership(courseId);
        VideoVO vo = videoService.update(id, request);
        return R.ok(vo);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("删除视频")
    @Operation(summary = "删除视频")
    public R<Void> delete(@PathVariable Long courseId, @PathVariable Long id) {
        videoService.assertCourseOwnership(courseId);
        videoService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "获取视频转码状态")
    public R<VideoStatusVO> getStatus(@PathVariable Long courseId, @PathVariable Long id) {
        videoService.assertCourseOwnership(courseId);
        return R.ok(videoService.getStatus(id));
    }
}
