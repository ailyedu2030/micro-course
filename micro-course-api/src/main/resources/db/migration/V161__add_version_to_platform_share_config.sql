-- =============================================================================
-- V161__add_version_to_platform_share_config.sql
-- -----------------------------------------------------------------------------
-- P1C-061: 平台分享配置表添加乐观锁 version 列，防止并发编辑覆盖。
--
-- 幂等模式（ADD COLUMN IF NOT EXISTS）：已含该列的环境中为 no-op。
-- =============================================================================

ALTER TABLE platform_share_config
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN platform_share_config.version IS '乐观锁版本号（P1C-061：防止并发编辑覆盖）';
