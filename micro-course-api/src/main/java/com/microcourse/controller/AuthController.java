package com.microcourse.controller;

import com.microcourse.audit.AuditedLog;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.microcourse.dto.ChangePasswordRequest;
import com.microcourse.dto.LoginRequest;
import com.microcourse.dto.LoginResponse;
import com.microcourse.dto.R;
import com.microcourse.dto.RefreshRequest;
import com.microcourse.dto.RegisterRequest;
import com.microcourse.dto.UpdateProfileRequest;
import com.microcourse.dto.UserApiKeyResponse;
import com.microcourse.dto.UserVO;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.AdminSettingService;
import com.microcourse.service.AuthService;
import com.microcourse.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "用户认证", description = "用户认证 API")
public class AuthController {

    private final AuthService authService;
    private final AdminSettingService adminSettingService;
    private final UserRepository userRepository;

    public AuthController(AuthService authService, AdminSettingService adminSettingService,
                           UserRepository userRepository) {
        this.authService = authService;
        this.adminSettingService = adminSettingService;
        this.userRepository = userRepository;
    }

    /**
     * P1C-002: 查询注册开关状态
     * GET /api/auth/registration-status
     * 权限: 公开（登录页加载时使用）
     */
    @GetMapping("/registration-status")
    @PreAuthorize("permitAll()")
    public R<java.util.Map<String, Object>> registrationStatus() {
        String enabled = adminSettingService.getByKey("registration_enabled");
        boolean isEnabled = enabled == null || "true".equalsIgnoreCase(enabled);
        return R.ok(java.util.Map.of("enabled", isEnabled));
    }

    @PostMapping("/login")
    @PreAuthorize("permitAll()")
    @AuditedLog("用户登录")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return R.ok(response);
    }

    @PostMapping("/register")
    @PreAuthorize("permitAll()")
    @AuditedLog("学生自助注册")
    public R<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        LoginResponse response = authService.register(request);
        return R.ok(response);
    }

    @PostMapping("/refresh")
    @PreAuthorize("permitAll()")
    public R<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        LoginResponse response = authService.refresh(request.getRefreshToken());
        return R.ok(response);
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    @AuditedLog("用户登出")
    public R<Void> logout() {
        authService.logout();
        return R.ok();
    }

    @GetMapping("/cas")
    @PreAuthorize("permitAll()")
    public R<LoginResponse> cas(
            @RequestParam("ticket") String ticket,
            @RequestParam(value = "state", required = false) String state) {
        LoginResponse response = authService.casLogin(ticket, state);
        return R.ok(response);
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public R<UserVO> me() {
        UserVO user = authService.getCurrentUser();
        return R.ok(user);
    }

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @AuditedLog("更新个人信息")
    public R<UserVO> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        UserVO user = authService.updateProfile(request);
        return R.ok(user);
    }

    @PutMapping("/me/password")
    @PreAuthorize("isAuthenticated()")
    @AuditedLog("修改密码")
    public R<java.util.Map<String, Object>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        // P1I-003: 返回 forceReLogin 标记，通知前端需要重新登录
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("forceReLogin", true);
        return R.ok(result);
    }

    @PostMapping("/me/avatar")
    @PreAuthorize("isAuthenticated()")
    public R<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        String avatarUrl = authService.uploadAvatar(file);
        return R.ok(avatarUrl);
    }

    /**
     * GET /api/auth/me/api-key
     * 查看当前用户的 Hermes API Key（脱敏）。未生成时返回 null。
     */
    @GetMapping("/me/api-key")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public R<UserApiKeyResponse> getMyApiKey() {
        Long currentUserId = SecurityUtil.getCurrentUserId();
        User user = userRepository.selectById(currentUserId);
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        if (user.getApiKey() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "未配置 API Key");
        }
        return R.ok(UserApiKeyResponse.maskedOnly(
                UserApiKeyResponse.mask(user.getApiKey()),
                user.getUpdatedAt() != null ? user.getUpdatedAt().toString() : null));
    }

    /**
     * POST /api/auth/me/api-key
     * 生成 / 重新生成当前用户的 Hermes API Key。返回明文（仅此一次）。
     */
    @PostMapping("/me/api-key")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public R<UserApiKeyResponse> generateMyApiKey() {
        Long currentUserId = SecurityUtil.getCurrentUserId();
        User user = userRepository.selectById(currentUserId);
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        String newKey = java.util.UUID.randomUUID().toString().replace("-", "")
                + java.util.UUID.randomUUID().toString().replace("-", "");
        user.setApiKey(newKey);
        user.setUpdatedAt(java.time.LocalDateTime.now());
        int rows = userRepository.updateById(user);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "生成 API Key 失败（乐观锁或无记录），请重试");
        }
        return R.ok(UserApiKeyResponse.full(
                newKey,
                UserApiKeyResponse.mask(newKey),
                user.getUpdatedAt().toString()));
    }

    /**
     * DELETE /api/auth/me/api-key
     * 撤销当前用户的 Hermes API Key。
     */
    @DeleteMapping("/me/api-key")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public R<Void> revokeMyApiKey() {
        Long currentUserId = SecurityUtil.getCurrentUserId();
        User user = userRepository.selectById(currentUserId);
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        user.setApiKey(null);
        user.setUpdatedAt(java.time.LocalDateTime.now());
        int rows = userRepository.updateById(user);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "撤销 API Key 失败（乐观锁或无记录）");
        }
        return R.ok();
    }
}