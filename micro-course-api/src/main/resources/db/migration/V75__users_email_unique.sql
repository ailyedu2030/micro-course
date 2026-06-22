-- V75__users_email_unique.sql · Email 唯一约束（仅限活跃用户）
-- E1: 补齐数据完整性——活跃用户 email 不重复
-- 依据: docs/数据字典.md v0.5
-- 日期: 2026-06-22
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_email ON users(email) WHERE email IS NOT NULL AND deleted_at IS NULL;
