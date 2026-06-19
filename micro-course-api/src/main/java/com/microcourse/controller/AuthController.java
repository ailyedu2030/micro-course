package com.microcourse.controller;

import com.microcourse.dto.ChangePasswordRequest;
import com.microcourse.dto.LoginRequest;
import com.microcourse.dto.LoginResponse;
import com.microcourse.dto.R;
import com.microcourse.dto.RefreshRequest;
import com.microcourse.dto.UpdateProfileRequest;
import com.microcourse.dto.UserVO;
import com.microcourse.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
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
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return R.ok(response);
    }

    @PostMapping("/refresh")
    public R<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        LoginResponse response = authService.refresh(request.getRefreshToken());
        return R.ok(response);
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public R<Void> logout() {
        authService.logout();
        return R.ok();
    }

    @GetMapping("/cas")
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
    public R<UserVO> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        UserVO user = authService.updateProfile(request);
        return R.ok(user);
    }

    @PutMapping("/me/password")
    @PreAuthorize("isAuthenticated()")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return R.ok();
    }

    @PostMapping("/avatar")
    @PreAuthorize("isAuthenticated()")
    public R<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        String avatarUrl = authService.uploadAvatar(file);
        return R.ok(avatarUrl);
    }
}