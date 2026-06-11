package com.microcourse.controller;

import com.microcourse.dto.PreferenceUpdateRequest;
import com.microcourse.dto.PreferenceVO;
import com.microcourse.dto.R;
import com.microcourse.service.NotificationPreferenceService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notification-preferences")
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;

    public NotificationPreferenceController(NotificationPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public R<PreferenceVO> getMyPreference() {
        Long userId = getCurrentUserId();
        PreferenceVO vo = preferenceService.getOrCreate(userId);
        return R.ok(vo);
    }

    @PutMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public R<PreferenceVO> updateMyPreference(@RequestBody PreferenceUpdateRequest request) {
        Long userId = getCurrentUserId();
        PreferenceVO vo = preferenceService.update(userId, request);
        return R.ok(vo);
    }

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }
        return null;
    }
}