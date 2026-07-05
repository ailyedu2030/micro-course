package com.microcourse.controller;

import com.microcourse.dto.AdminSettingVO;
import com.microcourse.dto.CasSettingsDTO;
import com.microcourse.dto.R;
import com.microcourse.dto.SettingUpdateRequest;
import com.microcourse.dto.ToggleRegisterRequest;
import com.microcourse.dto.UploadLimitRequest;
import com.microcourse.service.AdminSettingService;
import com.microcourse.util.FieldEncryptor;
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
    private final FieldEncryptor fieldEncryptor;

    public AdminSettingsController(AdminSettingService adminSettingService,
                                    FieldEncryptor fieldEncryptor) {
        this.adminSettingService = adminSettingService;
        this.fieldEncryptor = fieldEncryptor;
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
    public R<Void> toggleRegister(@Valid @RequestBody ToggleRegisterRequest request) {
        boolean enabled = request.getEnabled() != null ? request.getEnabled() : false;
        adminSettingService.upsert("allowRegistration", String.valueOf(enabled));
        return R.ok();
    }

    /**
     * 更新上传限制
     * PUT /api/admin/settings/upload
     * 权限: ADMIN
     */
    @PutMapping("/upload")
    @PreAuthorize("hasRole('ADMIN')")
    public R<Void> updateUploadLimit(@Valid @RequestBody UploadLimitRequest request) {
        int maxVideoSizeMb = request.getMaxVideoSizeMb() != null ? request.getMaxVideoSizeMb() : 100;
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
    public R<Void> updateCasConfig(@Valid @RequestBody CasSettingsDTO cas) {
        adminSettingService.upsert("cas_enabled", String.valueOf(cas.getEnabled()));
        adminSettingService.upsert("cas_server_url", cas.getServerUrl());
        adminSettingService.upsert("cas_service_url", cas.getServiceUrl());
        adminSettingService.upsert("cas_version", cas.getVersion());
        adminSettingService.upsert("cas_admin_username", fieldEncryptor.encrypt(cas.getAdminUsername()));
        adminSettingService.upsert("cas_super_admins", fieldEncryptor.encrypt(cas.getSuperAdmins() != null
                ? String.join(",", cas.getSuperAdmins()) : ""));
        adminSettingService.upsert("cas_validate_ssl", String.valueOf(cas.getValidateSsl()));
        return R.ok();
    }

    /**
     * 获取 CAS 配置
     * GET /api/admin/settings/cas
     * 权限: ADMIN
     */
    @GetMapping("/cas")
    @PreAuthorize("hasRole('ADMIN')")
    public R<CasSettingsDTO> getCasConfig() {
        CasSettingsDTO dto = new CasSettingsDTO();
        dto.setEnabled(Boolean.parseBoolean(
                adminSettingService.getByKey("cas_enabled") != null
                        ? adminSettingService.getByKey("cas_enabled") : "false"));
        dto.setServerUrl(adminSettingService.getByKey("cas_server_url"));
        dto.setServiceUrl(adminSettingService.getByKey("cas_service_url"));
        dto.setVersion(adminSettingService.getByKey("cas_version"));
        dto.setAdminUsername(fieldEncryptor.decrypt(adminSettingService.getByKey("cas_admin_username")));
        String superAdmins = fieldEncryptor.decrypt(adminSettingService.getByKey("cas_super_admins"));
        dto.setSuperAdmins(superAdmins != null && !superAdmins.isEmpty()
                ? List.of(superAdmins.split(",")) : List.of());
        dto.setValidateSsl(Boolean.parseBoolean(
                adminSettingService.getByKey("cas_validate_ssl") != null
                        ? adminSettingService.getByKey("cas_validate_ssl") : "true"));
        return R.ok(dto);
    }
}