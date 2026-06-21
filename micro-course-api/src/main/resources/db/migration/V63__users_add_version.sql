-- =============================================================================
-- V63__users_add_version.sql
-- -----------------------------------------------------------------------------
-- P0 状态机修复（Phase C-1 报告失真 → Round 6 真实补完）：
--   为 users 表补齐乐观锁 version 字段。
--
-- 背景：
--   - User 实体新增 @Version 乐观锁，配合 OptimisticLockerInnerInterceptor，
--     防止 ADMIN 并发修改用户状态时丢失更新；
--   - 但 users 建表时未含 version 列，MyBatis-Plus 在乐观锁拦截器开启后会生成
--     含 version 列的 UPDATE/SELECT SQL，若 DB 缺列则运行时报
--     "column version does not exist"。
--   - 因此以增量、幂等、非破坏的方式补列（与 V61/V62 同款范式）。
--
-- UX 零退化保证：
--   - ADD COLUMN IF NOT EXISTS：幂等，重复执行无副作用；
--   - NOT NULL DEFAULT 0：存量行自动回填 version=0，不破坏任何现有数据；
--   - 纯增量列，不改动任何既有列/约束/数据，API 与前端零影响。
-- 依据：docs/状态机设计.md §8.1（所有状态字段变更使用 version 字段防止并发）
-- 日期：2026-06-22
-- =============================================================================

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN users.version IS '乐观锁版本号（Phase C-1 修复 - Round 6 补完）';
