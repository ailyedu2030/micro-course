package com.microcourse.controller;

import com.microcourse.dto.AdminSettingVO;
import com.microcourse.dto.R;
import com.microcourse.service.AdminSettingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
     * 权限: ADMIN
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public R<List<AdminSettingVO>> getAll() {
        return R.ok(adminSettingService.getAll());
    }

    /**
     * 批量更新系统配置
     * PUT /api/admin/settings
     * 权限: ADMIN
     */
    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public R<Void> updateBatch(@RequestBody Map<String, String> settings) {
        adminSettingService.updateBatch(settings);
        return R.ok();
    }
}