-- =============================================================================
-- V69__fix_operation_logs_and_videos.sql
-- -----------------------------------------------------------------------------
-- 修复：operation_logs 双列冲突 + videos.file_size NOT NULL
-- 依据: docs/数据字典.md v0.5 + 交叉验证 R2 审查
-- 日期: 2026-06-22
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 修复1: operation_logs 表。
-- V2 创建了 success 列, V39 又追加了 is_success 列, 造成数据分裂。
-- 将 is_success 的数据回写到 success, 然后删除 is_success 列。
-- Entity OperationLog.java 已映射到 is_success, 同步将 success 列重命名为 is_success。
-- -----------------------------------------------------------------------------
-- Step 1: 将 V2 的 success 列数据迁移到 is_success（当 is_success 为 NULL 时）
UPDATE operation_logs SET is_success = success WHERE is_success IS NULL AND success IS NOT NULL;

-- Step 2: 删除旧的 success 列
ALTER TABLE operation_logs DROP COLUMN IF EXISTS success;

-- Step 3: 给 is_success 加 NOT NULL 约束（现有数据已迁移）
ALTER TABLE operation_logs ALTER COLUMN is_success SET NOT NULL;
ALTER TABLE operation_logs ALTER COLUMN is_success SET DEFAULT TRUE;

COMMENT ON COLUMN operation_logs.is_success IS '是否成功（合并自 V2.success）';

-- -----------------------------------------------------------------------------
-- 修复2: videos 表。
-- 数据字典要求 file_size BIGINT NOT NULL DEFAULT 0, 但 V5 建表时是 DEFAULT 0（无 NOT NULL）。
-- 给已有数据填充默认值后加 NOT NULL 约束。
-- -----------------------------------------------------------------------------
UPDATE videos SET file_size = 0 WHERE file_size IS NULL;
ALTER TABLE videos ALTER COLUMN file_size SET NOT NULL;
