package com.microcourse.controller;

import com.microcourse.dto.LearningProgressVO;
import com.microcourse.dto.ProgressCreateRequest;
import com.microcourse.dto.ProgressUpdateRequest;
import com.microcourse.dto.R;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.service.LearningProgressService;
import com.microcourse.util.SecurityUtil;
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
     * GET /api/learning-progress/progress?userId=&courseId=
     * 获取指定用户在某课程的学习进度。
     * IDOR 防护策略（3 层逐级放行）：
     *   L1: 未传 userId → 默认查自己（安全）
     *   L2: 传 userId == currentUserId → 查自己（安全）
     *   L3: ADMIN → 允许查任意用户（管理特权）
     *   L4: TEACHER + assertTeacherOwnsCourse → 仅允许查自己授课课程的学生进度
     *   L5: 其他角色 → 直接拒绝 NO_PERMISSION
     * 注意：STUDENT 无法通过此接口窃取其他用户进度。
     */
    @GetMapping("/progress")
    @PreAuthorize("isAuthenticated()")
    public R<List<LearningProgressVO>> getByUserAndCourse(
            @RequestParam(required = false) Long userId,
            @RequestParam Long courseId) {
        Long currentUserId = getCurrentUserId();
        Long targetUserId = (userId != null) ? userId : currentUserId;
        // IDOR: 非本人且非 ADMIN 时，TEACHER 需校验课程归属，否则拒绝
        if (!currentUserId.equals(targetUserId) && !SecurityUtil.isAdmin()) {
            if (hasRole("TEACHER")) {
                learningProgressService.assertTeacherOwnsCourse(currentUserId, courseId);
            } else {
                throw new BusinessException(ErrorCode.NO_PERMISSION);
            }
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

    /**
     * GET /api/learning-progress/progress/completion?userId=&courseId=
     * 获取用户课程完成度。支持不传 courseId（返回所有课程完成度）。
     * IDOR 防护策略：与 getByUserAndCourse 一致 ——
     *   非 ADMIN 用户必须 userId == currentUserId；
     *   TEACHER 可查自己授课课程的学生完成度（校验下沉 Service）。
     */
    @GetMapping("/progress/completion")
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, Object>> getCourseCompletion(
            @RequestParam Long userId,
            @RequestParam(required = false) Long courseId) {
        Long currentUserId = getCurrentUserId();
        // IDOR: 非本人且非 ADMIN 时，TEACHER 需校验课程归属，否则拒绝
        if (!currentUserId.equals(userId) && !SecurityUtil.isAdmin()) {
            if (hasRole("TEACHER") && courseId != null) {
                learningProgressService.assertTeacherOwnsCourse(currentUserId, courseId);
            } else {
                throw new BusinessException(ErrorCode.NO_PERMISSION);
            }
        }
        // P0-5: 支持无 courseId 时返回所有课程完成度 map
        if (courseId == null) {
            Map<String, Object> allCompletions = learningProgressService.getAllCourseCompletions(userId);
            return R.ok(allCompletions);
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