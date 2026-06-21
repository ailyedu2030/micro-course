-- =============================================================================
-- V64__teaching_classes_add_version.sql
-- -----------------------------------------------------------------------------
-- P0 状态机修复（Round 6）：确保 teaching_classes 表具备乐观锁 version 列，
-- 配合 TeachingClass 实体新增的 @Version 注解（防止并发状态修改丢失更新）。
--
-- 背景（诚实说明）：
--   - teaching_classes 在 V32__teaching_classes.sql 建表时即已包含
--     `version INTEGER NOT NULL DEFAULT 0` 列（见 V32 第 27 行）。
--   - 但历史上 TeachingClass 实体一直未挂 @Version 注解，乐观锁从未真正生效；
--     Round 6 为实体补上 @Version 后，必须保证任何环境下该列确实存在，
--     以免在乐观锁拦截器生成的 `... AND version = ?` SQL 上报错。
--   - 因此本 migration 以增量、幂等、非破坏的方式确保列存在（与 V61 enrollments
--     同款范式）；在已含该列的标准环境中为 no-op（IF NOT EXISTS 跳过）。
--
-- UX 零退化保证：
--   - ADD COLUMN IF NOT EXISTS：幂等，重复执行 / 列已存在时均无副作用；
--   - NOT NULL DEFAULT 0：若需新增列，存量行自动回填 version=0，不破坏任何现有数据；
--   - 纯增量列，不改动任何既有列 / 约束 / 数据，API 与前端零影响。
-- 依据：docs/状态机设计.md §8.1（所有状态字段变更使用 version 字段防止并发）
-- 日期：2026-06-22
-- =============================================================================

ALTER TABLE teaching_classes
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN teaching_classes.version IS '乐观锁版本号（Round 6：为 @Version 乐观锁提供列保障，防止并发状态修改丢失更新）';
