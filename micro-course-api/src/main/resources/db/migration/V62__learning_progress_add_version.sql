-- =============================================================================
-- V62__learning_progress_add_version.sql
-- -----------------------------------------------------------------------------
-- P0-6 修复（学习进度并发上报丢失更新）：为 learning_progress 表补齐乐观锁 version 字段。
--
-- 背景：
--   - LearningProgress 实体新增 @Version 乐观锁，防止同用户多端并发上报进度时
--     "最后一次写入覆盖前一次"导致进度丢失/倒退；
--   - 但 V9__gate3_progress_checkins.sql 建表时未含 version 列，且 MyBatis-Plus
--     BaseMapper 的 selectById/selectList 会生成含 version 列的显式 SQL，
--     若 DB 缺列则进度读取在运行时报 "column version does not exist"。
--   - 因此必须以增量、幂等、非破坏的方式补列（与 V61 enrollments 同款范式）。
--
-- UX 零退化保证：
--   - ADD COLUMN IF NOT EXISTS：幂等，重复执行无副作用；
--   - NOT NULL DEFAULT 0：存量行自动回填 version=0，不破坏任何现有数据；
--   - 纯增量列，不改动任何既有列/约束/数据，API 与前端零影响；
--   - total_watch_time 累加仍走原子 SQL（CON-003 修复），正常用户无感。
-- 依据：docs/状态机设计.md §8.1（所有状态字段变更使用 version 字段防止并发）
-- 日期：2026-06-22
-- =============================================================================

ALTER TABLE learning_progress
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN learning_progress.version IS '乐观锁版本号（P0-6：防止并发进度上报丢失更新）';
