package com.microcourse.controller;

import com.microcourse.dto.LearningProgressVO;
import com.microcourse.dto.ProgressCreateRequest;
import com.microcourse.dto.ProgressUpdateRequest;
import com.microcourse.dto.R;
import com.microcourse.service.LearningProgressService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@RestController
@RequestMapping("/api/learning-progress")
public class LearningProgressController {

    private final LearningProgressService learningProgressService;

    public LearningProgressController(LearningProgressService learningProgressService) {
        this.learningProgressService = learningProgressService;
    }

    @GetMapping("/progress")
    @PreAuthorize("isAuthenticated()")
    public R<List<LearningProgressVO>> getByUserAndCourse(
            @RequestParam Long userId,
            @RequestParam Long courseId) {
        Long currentUserId = getCurrentUserId();
        if (!currentUserId.equals(userId) && !hasRole("ADMIN")) {
            throw new com.microcourse.exception.BusinessException(com.microcourse.exception.ErrorCode.NO_PERMISSION);
        }
        List<LearningProgressVO> list = learningProgressService.getByUserAndCourse(userId, courseId);
        return R.ok(list);
    }

    @PostMapping("/progress")
    @PreAuthorize("isAuthenticated()")
    public R<LearningProgressVO> create(@Valid @RequestBody ProgressCreateRequest request) {
        LearningProgressVO vo = learningProgressService.create(request);
        return R.ok(vo);
    }

    @PutMapping("/progress/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<Void> updateProgress(@PathVariable Long id,
                                  @RequestBody ProgressUpdateRequest request) {
        Long userId = getCurrentUserId();
        learningProgressService.updateProgress(id, userId, request);
        return R.ok();
    }

    @GetMapping("/progress/completion")
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, Object>> getCourseCompletion(
            @RequestParam Long userId,
            @RequestParam Long courseId) {
        Long currentUserId = getCurrentUserId();
        if (!currentUserId.equals(userId) && !hasRole("ADMIN")) {
            throw new com.microcourse.exception.BusinessException(com.microcourse.exception.ErrorCode.NO_PERMISSION);
        }
        Map<String, Object> result = learningProgressService.getCourseCompletion(userId, courseId);
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
}