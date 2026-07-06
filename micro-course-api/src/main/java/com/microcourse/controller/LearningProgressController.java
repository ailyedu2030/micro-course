package com.microcourse.controller;

import com.microcourse.dto.LearningProgressVO;
import com.microcourse.dto.ProgressCreateRequest;
import com.microcourse.dto.ProgressUpdateRequest;
import com.microcourse.dto.R;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.service.LearningProgressService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/learning-progress")
public class LearningProgressController {

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
     * GET /api/users/{id}/learning-progress (权限矩阵 v4.1 路径别名)
     * 【P1-C 修复】为兼容旧路径, 添加路由别名委托到 /progress
     * 实际数据查询仍走 /api/learning-progress/progress
     */
    @GetMapping("/users/{id}/learning-progress")
    @PreAuthorize("isAuthenticated()")
    public R<List<LearningProgressVO>> getByUserAlias(@PathVariable Long id,
                                                        @RequestParam Long courseId) {
        return getByUserAndCourse(id, courseId);
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
    @PreAuthorize("isAuthenticated()")
    public R<LearningProgressVO> create(@Valid @RequestBody ProgressCreateRequest request) {
        Long userId = getCurrentUserId();
        request.setUserId(userId);
        LearningProgressVO vo = learningProgressService.create(request);
        return R.ok(vo);
    }

    @PutMapping("/progress/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<Void> updateProgress(@PathVariable Long id,
                                  @Valid @RequestBody ProgressUpdateRequest request) {
        Long userId = getCurrentUserId();
        learningProgressService.updateProgress(id, userId, request);
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
            try { return Long.parseLong(str); } catch (NumberFormatException ignored) { }
        }
        throw new BusinessException(ErrorCode.TOKEN_INVALID, "无法获取用户ID");
    }

    private boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        for (GrantedAuthority granted : auth.getAuthorities()) {
            if (granted.getAuthority().equals("ROLE_" + role)) return true;
        }
        return false;
    }

    private Long extractUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long) return (Long) principal;
        return Long.parseLong(principal.toString());
    }
}