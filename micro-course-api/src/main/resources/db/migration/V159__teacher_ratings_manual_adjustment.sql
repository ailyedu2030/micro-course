-- =============================================================================
-- V130__teacher_ratings_manual_adjustment.sql
-- -----------------------------------------------------------------------------
-- P0-008：教师评级手动调整标记 — 防止批量重算覆盖手动调整
-- 范围：teacher_ratings
-- =============================================================================

-- 1. 添加 manual_adjustment 标记列，默认 false
ALTER TABLE teacher_ratings
    ADD COLUMN IF NOT EXISTS manual_adjustment BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN teacher_ratings.manual_adjustment IS '是否手动调整：true=管理员手动调整，批量重算跳过';

-- 2. 修改 upsert 函数/逻辑无需变更，因为新增列有 DEFAULT FALSE
--    INSERT ON CONFLICT 不会重置 manual_adjustment = false
--    后续 Java 代码控制：手动调整时设 true，批量重算时跳过 true 的记录
