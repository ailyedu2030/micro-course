-- =============================================================================
-- V61__enrollments_add_version.sql
-- -----------------------------------------------------------------------------
-- P0-2 修复（选课状态机三重失守之二）：为 enrollments 表补齐乐观锁 version 字段。
--
-- 背景：
--   - Enrollment 实体新增 @Version 乐观锁，防止并发状态修改丢失更新；
--   - 但 V4__gate2_enrollments.sql 建表时未含 version 列，且 MyBatis-Plus
--     BaseMapper 的 selectById/selectList 会生成含 version 列的显式 SQL，
--     若 DB 缺列则所有选课读取在运行时报 "column version does not exist"。
--   - 因此必须以增量、幂等、非破坏的方式补列（与 V52 grades / V57 orders 同款范式）。
--
-- UX 零退化保证：
--   - ADD COLUMN IF NOT EXISTS：幂等，重复执行无副作用；
--   - NOT NULL DEFAULT 0：存量行自动回填 version=0，不破坏任何现有数据；
--   - 纯增量列，不改动任何既有列/约束/数据，API 与前端零影响。
-- 依据：docs/状态机设计.md §8.1（所有状态字段变更使用 version 字段防止并发）
-- 日期：2026-06-22
-- =============================================================================

ALTER TABLE enrollments
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN enrollments.version IS '乐观锁版本号（P0-2：防止并发状态修改丢失更新）';
