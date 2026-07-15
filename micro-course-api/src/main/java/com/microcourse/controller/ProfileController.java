package com.microcourse.controller;

import com.microcourse.dto.*;
import com.microcourse.service.AuthService;
import com.microcourse.service.NotificationPreferenceService;
import com.microcourse.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
@Tag(name = "个人设置", description = "个人设置端点（别名 /api/auth/me，供教师端等前端统一入口）")
public class ProfileController {

    private final AuthService authService;
    private final NotificationPreferenceService notificationPreferenceService;

    public ProfileController(AuthService authService, NotificationPreferenceService notificationPreferenceService) {
        this.authService = authService;
        this.notificationPreferenceService = notificationPreferenceService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "获取个人信息")
    public R<UserVO> getProfile() {
        return R.ok(authService.getCurrentUser());
    }

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "更新个人信息")
    public R<Void> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        authService.updateProfile(request);
        return R.ok();
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "修改密码")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return R.ok();
    }

    @PutMapping("/notifications")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "设置通知偏好")
    public R<Void> updateNotificationPreferences(@Valid @RequestBody PreferenceUpdateRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        notificationPreferenceService.update(userId, request);
        return R.ok();
    }
}
