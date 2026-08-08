package com.microcourse.controller;

import com.microcourse.dto.LearningProgressVO;
import com.microcourse.dto.SectionVideoProgressReportRequest;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.microcourse.dto.ProgressCreateRequest;
import com.microcourse.dto.ProgressUpdateRequest;
import com.microcourse.dto.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.service.LearningProgressService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/learning-progress")
@Tag(name = "学习进度", description = "学习进度 API")
public class LearningProgressController {

    private static final Logger log = LoggerFactory.getLogger(LearningProgressController.class);

    private final LearningProgressService learningProgressService;

    public LearningProgressController(LearningProgressService learningProgressService) {
        this.learningProgressService = learningProgressService;
    }

    /**
     * GET /api/learning-progress
     * 根路径返回错误提示（避免无handler时返回500）
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<Void> root() {
        return R.fail(ErrorCode.BAD_REQUEST_PARAM);
    }

    /**
     * GET /api/learning-progress/progress?userId=&courseId=
     * 获取指定用户在某课程的学习进度（IDOR 防护已下沉 Service 层）。
     */
    @GetMapping("/progress")
    @PreAuthorize("isAuthenticated()")
    public R<List<LearningProgressVO>> getByUserAndCourse(
            @RequestParam(required = false) Long userId,
            @RequestParam Long courseId) {
        Long currentUserId = getCurrentUserId();
        Long targetUserId = (userId != null) ? userId : currentUserId;
        List<LearningProgressVO> list = learningProgressService.getProgressWithGuard(currentUserId, targetUserId, courseId);
        return R.ok(list);
    }

    @PostMapping("/progress")
    @PreAuthorize("hasRole('STUDENT')")
    public R<LearningProgressVO> create(@Valid @RequestBody ProgressCreateRequest request) {
        Long userId = getCurrentUserId();
        request.setUserId(userId);
        LearningProgressVO vo = learningProgressService.create(request);
        return R.ok(vo);
    }

    @PutMapping("/progress/{id}")
    @PreAuthorize("hasRole('STUDENT')")
    public R<Void> updateProgress(@PathVariable Long id,
                                  @Valid @RequestBody ProgressUpdateRequest request) {
        Long userId = getCurrentUserId();
        learningProgressService.updateProgress(id, userId, request);
        return R.ok();
    }

    /**
     * G3-P0-5: PUT /api/learning-progress/{courseId}/sections/{sectionId}/video-progress
     * 学生播放器翻页/音频结束上报"本课时播放进度"。
     * <p>
     * 服务端计算 {@code video_progress = played/total}（0-100，max 1.0）写入 learning_progress，
     * 供 evaluateFlow 的 SKIP_IF_KNOWN 服务端读取（纯 PPT/HTML 学习场景此前该字段恒 null
     * → SKIP 规则永不命中，教师配置的 flow 规则形同虚设）。仅 STUDENT 可调用，
     * userId 取 SecurityContext，不信任客户端。
     * </p>
     */
    @PutMapping("/{courseId}/sections/{sectionId}/video-progress")
    @PreAuthorize("hasRole('STUDENT')")
    public R<Void> reportVideoProgress(@PathVariable Long courseId,
                                       @PathVariable Long sectionId,
                                       @Valid @RequestBody SectionVideoProgressReportRequest request) {
        Long userId = getCurrentUserId();
        learningProgressService.updateVideoProgress(userId, courseId, sectionId,
                request.getPlayedSeconds(), request.getTotalSeconds());
        return R.ok();
    }

    /**
     * GET /api/learning-progress/progress/completion?userId=&courseId=
     * 获取用户课程完成度（IDOR 防护已下沉 Service 层）。
     */
    @GetMapping("/progress/completion")
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, Object>> getCourseCompletion(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long courseId) {
        Long currentUserId = getCurrentUserId();
        if (userId == null) userId = currentUserId;
        Map<String, Object> result = learningProgressService.getCourseCompletionWithGuard(currentUserId, userId, courseId);
        return R.ok(result);
    }

    @GetMapping("/study-days")
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, Object>> getStudyDays(Authentication authentication) {
        Long userId = extractUserId(authentication);
        Map<String, Object> result = learningProgressService.getStudyDays(userId);
        return R.ok(result);
    }

    /**
     * R8 P0-3: 批量获取用户在多门课程中的学习进度（解决 MyCourses N+1）。
     * GET /api/learning-progress/progress/batch?courseIds=1,2,3
     * IDOR 防护：前端传的 userId 为当前登录用户，后端直接取 token 中的 userId。
     */
    @GetMapping("/progress/batch")
    @PreAuthorize("isAuthenticated()")
    public R<List<LearningProgressVO>> batchGetByUserAndCourses(
            @RequestParam String courseIds) {
        Long userId = getCurrentUserId();
        List<Long> ids = java.util.Arrays.stream(courseIds.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(java.util.stream.Collectors.toList());
        List<LearningProgressVO> list = learningProgressService.batchGetByUserAndCourses(userId, ids);
        return R.ok(list);
    }

    @GetMapping("/total-time")
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, Object>> getTotalTime(Authentication authentication) {
        Long userId = extractUserId(authentication);
        Map<String, Object> result = learningProgressService.getTotalTime(userId);
        return R.ok(result);
    }

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Long) return (Long) principal;
        if (principal instanceof Number) return ((Number) principal).longValue();
        if (principal instanceof String str) {
            try { return Long.parseLong(str); } catch (NumberFormatException e) {
                log.warn("无法将principal解析为Long: {}", str);
            }
        }
        throw new BusinessException(ErrorCode.TOKEN_INVALID, "无法获取用户ID");
    }

    private Long extractUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long) return (Long) principal;
        return Long.parseLong(principal.toString());
    }
}
