package com.microcourse.controller;

import com.microcourse.dto.AdminSettingVO;
import com.microcourse.dto.R;
import com.microcourse.dto.SettingUpdateRequest;
import com.microcourse.service.AdminSettingService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统配置控制器
 *
 * @author Phase9-Development-Team
 * @since 2026-06-12
 */
@RestController
@RequestMapping("/api/admin/settings")
public class AdminSettingsController {

    private final AdminSettingService adminSettingService;

    public AdminSettingsController(AdminSettingService adminSettingService) {
        this.adminSettingService = adminSettingService;
    }

    /**
     * 获取所有系统配置
     * GET /api/admin/settings
     * 权限: ADMIN, ACADEMIC
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<List<AdminSettingVO>> getAll() {
        return R.ok(adminSettingService.getAll());
    }

    /**
     * 批量更新系统配置
     * PUT /api/admin/settings
     * 权限: ADMIN（只有 ADMIN 可以修改设置）
     */
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public R<Void> updateBatch(@Valid @RequestBody List<SettingUpdateRequest> settings) {
        adminSettingService.updateBatch(settings);
        return R.ok();
    }

    /**
     * 开关注册
     * PUT /api/admin/settings/register
     * 权限: ADMIN
     */
    @PutMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    public R<Void> toggleRegister(@RequestParam Boolean enabled) {
        adminSettingService.upsert("registration_enabled", String.valueOf(enabled));
        return R.ok();
    }

    /**
     * 更新上传限制
     * PUT /api/admin/settings/upload
     * 权限: ADMIN
     */
    @PutMapping("/upload")
    @PreAuthorize("hasRole('ADMIN')")
    public R<Void> updateUploadLimit(@RequestParam Integer maxVideoSizeMb) {
        adminSettingService.upsert("max_video_size_mb", String.valueOf(maxVideoSizeMb));
        return R.ok();
    }

    /**
     * 更新 CAS 配置
     * PUT /api/admin/settings/cas
     * 权限: ADMIN
     */
    @PutMapping("/cas")
    @PreAuthorize("hasRole('ADMIN')")
    public R<Void> updateCasConfig(@RequestParam String casServerUrl, @RequestParam String casServiceUrl) {
        adminSettingService.upsert("cas_server_url", casServerUrl);
        adminSettingService.upsert("cas_service_url", casServiceUrl);
        return R.ok();
    }
}