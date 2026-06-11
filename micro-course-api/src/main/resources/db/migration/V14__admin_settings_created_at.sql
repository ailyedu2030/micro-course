-- V14__admin_settings_created_at.sql
-- 依据: docs/数据字典.md v0.5 §8.1
-- 日期: 2026-06-12
-- 修复: V12 缺少 created_at 字段

ALTER TABLE admin_settings ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;