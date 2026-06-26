-- V57__fix_orders_bundle_fk.sql
-- 修复 V51 bundle_id FK 指向错误的表: course_bundles(id) → 实际创建时指向 courses(id) (历史 bug)
-- 同时为 orders 表增加 version 乐观锁字段

ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_bundle_id_fkey;
ALTER TABLE orders ADD CONSTRAINT orders_bundle_id_fkey
    FOREIGN KEY (bundle_id) REFERENCES course_bundles(id);

ALTER TABLE orders ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;
