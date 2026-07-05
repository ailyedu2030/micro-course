-- P2-8 修复: 为 admin_settings 增加 value_type 和 description 字段
-- 前端通过 value_type 判断类型，不再依赖 BOOLEAN_KEYS 硬编码
-- 合并 V155 + V156 内容

ALTER TABLE admin_settings ADD COLUMN IF NOT EXISTS value_type VARCHAR(20) DEFAULT 'STRING';
ALTER TABLE admin_settings ADD COLUMN IF NOT EXISTS description VARCHAR(500);

COMMENT ON COLUMN admin_settings.value_type IS '值类型: STRING / BOOLEAN / NUMBER / JSON';

-- 为已有记录设置合理的默认 value_type
UPDATE admin_settings SET value_type = 'BOOLEAN' WHERE setting_key IN (
    'allowRegistration', 'maintenanceMode',
    'useSsl', 'useTls',
    'requireNumber', 'requireSpecialChar', 'lockOnFailure', 'require2FA',
    'cas_enabled', 'cas_validate_ssl',
    'register.enabled'
);

UPDATE admin_settings SET value_type = 'NUMBER' WHERE setting_key IN (
    'max_video_size_mb',
    'maxUploadSize', 'sessionTimeout',
    'smtpPort',
    'minPasswordLength', 'maxFailAttempts', 'lockDuration', 'tokenExpiry', 'refreshTokenExpiry',
    'upload.max_size'
);

UPDATE admin_settings SET value_type = 'JSON' WHERE setting_key IN (
    'cas_super_admins'
);