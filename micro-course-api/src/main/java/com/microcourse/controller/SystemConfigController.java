package com.microcourse.controller;

import com.microcourse.dto.AdminSettingVO;
import com.microcourse.dto.R;
import com.microcourse.service.AdminSettingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 公开系统配置控制器（Round 5-3 P1-10 新增）。
 *
 * <p>背景：权限矩阵 v2.0 定义 {@code GET /api/system-configs/public}（READ_PUBLIC_CONFIG = 所有用户，
 * 含未登录），但代码此前未实现，前端首屏（站点名称/Logo/是否开放注册等）只能绕路调用受保护的
 * {@code /api/admin/settings}（ADMIN/ACADEMIC）而被 403 拒绝。本端点仅暴露<b>白名单内的非敏感公开键</b>，
 * 数据源复用既有 {@link AdminSettingService}，不新建表、不写迁移。</p>
 *
 * <p>权限：所有用户（含未登录）。本方法<b>不加 {@code @PreAuthorize}</b>，并在
 * {@code SecurityConfig} 中以 {@code GET /api/system-configs/public → permitAll} 放行；
 * CAS/密码/上传上限等敏感配置永不在此返回，避免信息泄露。</p>
 */
@RestController
@RequestMapping("/api/system-configs")
public class SystemConfigController {

    /**
     * 公开配置键白名单 —— 仅站点展示型、非敏感元数据。
     * 任何未列入此集合的配置（如 cas_*、max_video_size_mb 等）一律不返回。
     */
    private static final Set<String> PUBLIC_KEYS = Set.of(
            "site_name",
            "site_logo",
            "platform_name",
            "platform_logo",
            "allowRegistration",
            "icp_number",
            "footer_text",
            "copyright"
    );

    private final AdminSettingService adminSettingService;

    public SystemConfigController(AdminSettingService adminSettingService) {
        this.adminSettingService = adminSettingService;
    }

    /**
     * GET /api/system-configs/public — 获取公开系统配置（所有用户，含未登录）。
     *
     * <p>返回 {@code {key: value}} 映射，仅含 {@link #PUBLIC_KEYS} 白名单命中的项；
     * 若数据库未配置任何公开键，则返回空对象（仍为 200），端点真正可用、可被前端安全消费。</p>
     */
    @GetMapping("/public")
    @PreAuthorize("permitAll()")
    public R<Map<String, String>> publicConfigs() {
        Map<String, String> result = new LinkedHashMap<>();
        for (AdminSettingVO vo : adminSettingService.getAll()) {
            if (vo.getSettingKey() != null && PUBLIC_KEYS.contains(vo.getSettingKey())) {
                result.put(vo.getSettingKey(), vo.getSettingValue());
            }
        }
        return R.ok(result);
    }
}
