-- V12__admin_settings.sql · 系统配置表
-- 依据: docs/数据字典.md v0.5 §8.1
-- 日期: 2026-06-12

CREATE TABLE admin_settings (
    id           BIGSERIAL   PRIMARY KEY,
    setting_key  VARCHAR(64) UNIQUE NOT NULL,
    setting_value TEXT,
    description  VARCHAR(255),
    updated_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 预置系统配置数据
INSERT INTO admin_settings (setting_key, setting_value, description) VALUES
    ('platform.name',      '微课管理平台',   '平台名称'),
    ('platform.logo',      '/logo.png',      '平台 Logo 路径'),
    ('platform.icp',       '',               'ICP 备案号'),
    ('register.enabled',   'true',           '是否开放注册'),
    ('upload.max_size',    '2147483648',      '文件上传最大字节数（2GB）'),
    ('cas.server_url',     '',               'CAS 服务器地址'),
    ('cas.login_url',      '',               'CAS 登录页地址');