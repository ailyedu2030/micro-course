package com.microcourse.controller;

import com.microcourse.dto.ChangePasswordRequest;
import com.microcourse.dto.PreferenceUpdateRequest;
import com.microcourse.dto.R;
import com.microcourse.dto.UpdateProfileRequest;
import com.microcourse.dto.UserVO;
import com.microcourse.service.AuthService;
import com.microcourse.service.NotificationPreferenceService;
import com.microcourse.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人信息控制器 — 提供 /api/profile/* 别名路由。
 *
 * <p>历史背景：
 * 原 AuthController 类级 {@code @RequestMapping("/api/auth")} 下错误声明了
 * {@code @GetMapping("/api/profile")} 等路径，实际产生 {@code /api/auth/api/profile}
 * 的伪路由。本控制器在正确路径 {@code /api/profile} 下注册，保持与前端期待路径一致。
 *
 * <p>核心 API 路径：
 * <ul>
 *   <li>{@code POST /api/auth/me} — 主入口（前端当前使用）</li>
 *   <li>{@code GET /api/profile} — 个人信息别名</li>
 *   <li>{@code PUT /api/profile} — 更新资料别名</li>
 *   <li>{@code POST /api/profile/change-password} — 修改密码别名</li>
 *   <li>{@code PUT /api/profile/notifications} — 通知偏好别名</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/profile")
@Tag(name = "个人信息", description = "个人信息别名路由（/api/profile/*）")
public class ProfileController {

    private final AuthService authService;
    private final NotificationPreferenceService notificationPreferenceService;

    public ProfileController(AuthService authService,
                             NotificationPreferenceService notificationPreferenceService) {
        this.authService = authService;
        this.notificationPreferenceService = notificationPreferenceService;
    }

    /**
     * GET /api/profile
     * 获取个人信息（/api/auth/me 的别名）
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "获取个人信息")
    public R<UserVO> getProfile() {
        UserVO user = authService.getCurrentUser();
        return R.ok(user);
    }

    /**
     * PUT /api/profile
     * 更新个人信息（/api/auth/me PUT 的别名）
     */
    @PutMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "更新个人信息")
    public R<UserVO> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        UserVO user = authService.updateProfile(request);
        return R.ok(user);
    }

    /**
     * POST /api/profile/change-password
     * 修改密码（/api/auth/me/password 的别名）
     */
    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "修改密码")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return R.ok();
    }

    /**
     * PUT /api/profile/notifications
     * 设置通知偏好
     */
    @PutMapping("/notifications")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "设置通知偏好")
    public R<Void> updateNotificationPreferences(@Valid @RequestBody PreferenceUpdateRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        notificationPreferenceService.update(userId, request);
        return R.ok();
    }
}
