package com.microcourse.controller;

import com.microcourse.dto.LearningProgressVO;
import com.microcourse.dto.ProgressCreateRequest;
import com.microcourse.dto.ProgressUpdateRequest;
import com.microcourse.dto.R;
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
        return R.fail(400, "缺少查询参数，请使用 /api/learning-progress/progress?courseId=X");
    }

    @GetMapping("/progress")
    @PreAuthorize("isAuthenticated()")
    public R<List<LearningProgressVO>> getByUserAndCourse(
            @RequestParam(required = false) Long userId,
            @RequestParam Long courseId) {
        Long currentUserId = getCurrentUserId();
        // 如果未传 userId，默认查自己的进度
        Long targetUserId = (userId != null) ? userId : currentUserId;
        if (!currentUserId.equals(targetUserId) && !hasRole("ADMIN")) {
            throw new com.microcourse.exception.BusinessException(com.microcourse.exception.ErrorCode.NO_PERMISSION);
        }
        List<LearningProgressVO> list = learningProgressService.getByUserAndCourse(targetUserId, courseId);
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

    @GetMapping("/progress/completion")
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, Object>> getCourseCompletion(
            @RequestParam Long userId,
            @RequestParam(required = false) Long courseId) {
        Long currentUserId = getCurrentUserId();
        if (!currentUserId.equals(userId) && !hasRole("ADMIN")) {
            throw new com.microcourse.exception.BusinessException(com.microcourse.exception.ErrorCode.NO_PERMISSION);
        }
        if (courseId == null) {
            return R.ok(new java.util.HashMap<>());
        }
        Map<String, Object> result = learningProgressService.getCourseCompletion(userId, courseId);
        return R.ok(result);
    }

    @GetMapping("/study-days")
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, Object>> getStudyDays(Authentication authentication) {
        Long userId = extractUserId(authentication);
        Map<String, Object> result = learningProgressService.getStudyDays(userId);
        return R.ok(result);
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
        throw new com.microcourse.exception.BusinessException(com.microcourse.exception.ErrorCode.TOKEN_INVALID);
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