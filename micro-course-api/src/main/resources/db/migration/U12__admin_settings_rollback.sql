-- U12__admin_settings_rollback.sql · 回滚系统配置表
-- 对应迁移: V12__admin_settings.sql

DROP TABLE IF EXISTS admin_settings CASCADE;
