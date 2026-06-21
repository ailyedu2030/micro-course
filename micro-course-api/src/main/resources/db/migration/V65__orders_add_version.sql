-- =============================================================================
-- V65__orders_add_version.sql
-- -----------------------------------------------------------------------------
-- Round 6-3 修复（订单状态机收口）：显式声明 orders 表乐观锁 version 字段。
--
-- 诚实说明（避免假修复）：
--   - orders.version 列实际已由 V57__fix_orders_bundle_fk.sql 第 9 行创建：
--       ALTER TABLE orders ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;
--   - 本 V65 与 Round 6-1（V63 users）/ Round 6-2（V64 teaching_classes）配套，
--     作为「订单状态机三件套」中 DB 侧的显式、幂等的兜底声明，保证即使某些
--     历史环境漏执行 V57，orders 表仍具备 version 列，乐观锁不致运行期报
--     "column version does not exist"。
--
-- UX 零退化保证：
--   - ADD COLUMN IF NOT EXISTS：幂等，列已存在时为 no-op，重复执行无副作用；
--   - NOT NULL DEFAULT 0：存量行自动回填 version=0，不破坏任何现有数据；
--   - 纯增量列，不改动任何既有列/约束/数据，API 与前端零影响。
-- 依据：docs/状态机设计.md（所有状态字段变更使用 version 字段防止并发）
-- 日期：2026-06-22
-- =============================================================================

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN orders.version IS '乐观锁版本号（Round 6-3：防止并发状态修改丢失更新；列实际由 V57 首建，此处幂等兜底）';
