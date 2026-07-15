package com.microcourse.controller;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.dto.VideoVO;
import com.microcourse.service.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.hibernate.validator.constraints.Range;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses/{courseId}/videos")
@Tag(name = "课程视频管理", description = "视频管理课程子资源路径（与/api/videos平行，保留向后兼容）")
public class CourseVideoController {

    private final VideoService videoService;

    public CourseVideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    @GetMapping
    @Operation(summary = "获取课程下视频列表")
    public R<PageResult<VideoVO>> listByCourse(
            @PathVariable Long courseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") @Range(min = 1, max = 10000) int size) {
        return R.ok(videoService.page(courseId, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取视频详情")
    public R<VideoVO> getById(@PathVariable Long courseId, @PathVariable Long id) {
        VideoVO vo = videoService.getById(id);
        return R.ok(vo);
    }
}
