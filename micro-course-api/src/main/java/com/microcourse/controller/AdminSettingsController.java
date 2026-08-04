package com.microcourse.controller;

import com.microcourse.dto.AdminSettingVO;
import com.microcourse.dto.CasSettingsDTO;
import com.microcourse.dto.R;
import com.microcourse.dto.SettingUpdateRequest;
import com.microcourse.dto.ToggleRegisterRequest;
import com.microcourse.dto.UploadLimitRequest;
import com.microcourse.service.AdminSettingService;
import com.microcourse.util.FieldEncryptor;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import jakarta.validation.Valid;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

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
     * B10.5 发送测试邮件（真实 SMTP 发送，替代此前前端模拟占位）。
     * 使用系统设置中保存的 SMTP 配置向配置邮箱自发送一封测试邮件。
     */
    @PostMapping("/send-test-email")
    @PreAuthorize("hasRole('ADMIN')")
    public R<Void> sendTestEmail() {
        Map<String, String> cfg = adminSettingService.getAll().stream()
                .collect(Collectors.toMap(AdminSettingVO::getSettingKey,
                        v -> v.getSettingValue() == null ? "" : v.getSettingValue(), (a, b) -> b));
        String host = cfg.getOrDefault("smtpHost", "").trim();
        String username = cfg.getOrDefault("smtpUsername", "").trim();
        String password = cfg.getOrDefault("smtpPassword", "");
        String fromName = cfg.getOrDefault("fromName", "微课平台").trim();
        if (host.isEmpty() || username.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "请先填写完整的邮件配置");
        }
        int port;
        try {
            port = Integer.parseInt(cfg.getOrDefault("smtpPort", "587").trim());
        } catch (NumberFormatException e) {
            port = 587;
        }
        boolean ssl = "true".equalsIgnoreCase(cfg.getOrDefault("useSsl", "false").trim());
        boolean tls = "true".equalsIgnoreCase(cfg.getOrDefault("useTls", "false").trim());

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.enable", String.valueOf(ssl));
        props.put("mail.smtp.starttls.enable", String.valueOf(tls));
        props.put("mail.smtp.timeout", "8000");
        props.put("mail.smtp.connectiontimeout", "8000");
        try {
            MimeMessage msg = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setFrom(username, fromName);
            helper.setTo(username);
            helper.setSubject("微课平台 - SMTP 配置测试邮件");
            helper.setText("这是一封由微课管理平台发送的测试邮件，用于验证 SMTP 配置是否可用。", false);
            sender.send(msg);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "邮件发送失败: " + e.getMessage());
        }
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
     * 权限: ADMIN/ACADEMIC (与前端路由一致)
     */
    @PutMapping("/cas")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
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
     * 权限: ADMIN/ACADEMIC (与前端 /admin/settings 路由一致)
     */
    @GetMapping("/cas")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
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
