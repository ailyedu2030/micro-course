package com.microcourse.controller;

import com.microcourse.dto.PlatformShareConfigDTO;
import com.microcourse.dto.R;
import com.microcourse.service.PlatformShareConfigService;
import com.microcourse.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 平台分享配置控制器
 *
 * @author Phase9-Development-Team
 * @since 2026-07-03
 */
@RestController
@RequestMapping("/api/admin/platform-share-config")
@PreAuthorize("hasRole('ADMIN')")
public class PlatformShareConfigController {

    private final PlatformShareConfigService service;

    public PlatformShareConfigController(PlatformShareConfigService service) {
        this.service = service;
    }

    /**
     * 获取所有平台分享配置
     * GET /api/admin/platform-share-config
     * 权限: ADMIN
     */
    @GetMapping
    public R<List<PlatformShareConfigDTO>> listAll() {
        return R.ok(service.listAll());
    }

    /**
     * 根据 key 获取单个配置
     * GET /api/admin/platform-share-config/{key}
     * 权限: ADMIN
     */
    @GetMapping("/{key}")
    public R<PlatformShareConfigDTO> getByKey(@PathVariable String key) {
        return R.ok(service.findByKey(key).orElse(null));
    }

    /**
     * 更新或创建配置
     * PUT /api/admin/platform-share-config/{key}
     * 权限: ADMIN
     */
    @PutMapping("/{key}")
    public R<PlatformShareConfigDTO> upsert(@PathVariable String key,
                                            @Valid @RequestBody PlatformShareConfigDTO dto) {
        dto.setConfigKey(key);
        dto.setUpdatedBy(SecurityUtil.getCurrentUserId());
        service.upsert(dto);
        return R.ok(dto);
    }
}
