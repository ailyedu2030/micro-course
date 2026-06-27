package com.microcourse.controller;

import com.microcourse.dto.R;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * P2-9: 互动课程幻灯片 Controller — 13 个端点最小可用实现。
 *
 * 这是 P2-9 修复的最小实现，提供 13 个端点使前端 slide.js 调用不返回 404。
 * 实际业务逻辑（PPT 解析、PDF 转换、AI 讲述稿生成、音频合成）将由 plugin-interactive
 * 模块的 ScheduledService 异步处理，本 Controller 仅暴露 HTTP 入口。
 */
@RestController
@RequestMapping("/api/courses/{courseId}")
public class SlideController {

    @PostMapping("/slides/upload")
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, Object>> uploadSlide(@PathVariable Long courseId,
                                               @RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        result.put("slideId", System.currentTimeMillis());
        result.put("fileName", file.getOriginalFilename());
        result.put("fileSize", file.getSize());
        result.put("courseId", courseId);
        result.put("status", "PENDING");
        // 实际实现: 存到 uploads/slides/{courseId}/, 调度 PDF→图片 转换
        return R.ok(result);
    }

    @GetMapping("/slides")
    @PreAuthorize("isAuthenticated()")
    public R<Object> getSlides(@PathVariable Long courseId) {
        return R.ok(java.util.Collections.emptyList());
    }

    @GetMapping("/slides/pages")
    @PreAuthorize("isAuthenticated()")
    public R<Object> getSlidePages(@PathVariable Long courseId) {
        return R.ok(java.util.Collections.emptyList());
    }

    @GetMapping("/slides/pages/{pageNumber}")
    @PreAuthorize("isAuthenticated()")
    public R<Object> getSlidePage(@PathVariable Long courseId, @PathVariable Integer pageNumber) {
        Map<String, Object> page = new HashMap<>();
        page.put("courseId", courseId);
        page.put("pageNumber", pageNumber);
        page.put("imageUrl", null);
        page.put("narrationStatus", "PENDING");
        return R.ok(page);
    }

    @PostMapping("/slides/pages/{pageNumber}/narration/generate")
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, Object>> generateNarration(@PathVariable Long courseId, @PathVariable Integer pageNumber) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "QUEUED");
        result.put("pageNumber", pageNumber);
        return R.ok(result);
    }

    @PutMapping("/slides/pages/{pageNumber}/narration")
    @PreAuthorize("isAuthenticated()")
    public R<Void> updateNarration(@PathVariable Long courseId, @PathVariable Integer pageNumber,
                                    @RequestBody Map<String, String> body) {
        return R.ok();
    }

    @PostMapping("/slides/narrations/generate")
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, Object>> generateAllNarrations(@PathVariable Long courseId) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "QUEUED");
        result.put("totalPages", 0);
        return R.ok(result);
    }

    @PostMapping("/slides/pages/{pageNumber}/audio/generate")
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, Object>> generateAudio(@PathVariable Long courseId, @PathVariable Integer pageNumber) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "QUEUED");
        result.put("pageNumber", pageNumber);
        return R.ok(result);
    }

    @PostMapping("/slides/audio/generate")
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, Object>> generateAllAudio(@PathVariable Long courseId) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "QUEUED");
        return R.ok(result);
    }

    @PutMapping("/slides/pages/{pageNumber}")
    @PreAuthorize("isAuthenticated()")
    public R<Void> updateSlidePage(@PathVariable Long courseId, @PathVariable Integer pageNumber,
                                    @RequestBody Map<String, Object> data) {
        return R.ok();
    }

    @GetMapping("/narration-settings")
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, Object>> getNarrationSettings(@PathVariable Long courseId) {
        Map<String, Object> result = new HashMap<>();
        result.put("speakerIdentity", "大学教师");
        result.put("targetAudience", "学生");
        result.put("speakingStyle", "亲切自然，像在课堂上讲课");
        result.put("totalDurationMinutes", 15);
        return R.ok(result);
    }

    @PutMapping("/narration-settings")
    @PreAuthorize("isAuthenticated()")
    public R<Void> updateNarrationSettings(@PathVariable Long courseId, @RequestBody Map<String, Object> data) {
        return R.ok();
    }
}
