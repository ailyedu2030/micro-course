-- =============================================================================
-- V73__exercise_records_version_and_payments_fk.sql
-- -----------------------------------------------------------------------------
-- DB-P0-01: exercise_records 加 @Version 乐观锁字段
-- DB-P0-02: payments.order_id FK ON DELETE CASCADE 修复
--
-- 背景：
--   - DB-P0-01: exercise_records 表并发提交/评分时缺乏乐观锁保护，
--     可能导致并发更新丢失（如同时提交答案和自动评分）。
--     参照 V63/V64/V65 同款范式，补 version 列。
--   - DB-P0-02: payments.order_id FK 原为简单 REFERENCES orders(id)，
--     无级联删除——删除订单时 payments 中遗留孤儿记录。
--     改为 ON DELETE CASCADE，确保废除订单时支付流水同步清理。
--
-- UX 零退化保证：
--   - ADD COLUMN IF NOT EXISTS：幂等
--   - NOT NULL DEFAULT 0：存量行自动回填
--   - FK 变更通过 DROP + ADD 实现，幂等执行无副作用
-- 日期：2026-06-22
-- =============================================================================

-- ── DB-P0-01: exercise_records 乐观锁 ──
ALTER TABLE exercise_records
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN exercise_records.version IS '乐观锁版本号（P0-01 修复）';

-- ── DB-P0-02: payments FK CASCADE ──
DO $$
DECLARE
    fk_name TEXT;
BEGIN
    SELECT tc.constraint_name INTO fk_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
      ON tc.constraint_name = kcu.constraint_name
    WHERE tc.table_name = 'payments'
      AND tc.constraint_type = 'FOREIGN KEY'
      AND kcu.column_name = 'order_id'
    LIMIT 1;

    IF fk_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE payments DROP CONSTRAINT %I', fk_name);
    END IF;

    ALTER TABLE payments
        ADD CONSTRAINT fk_payments_order
        FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE;
END $$;
