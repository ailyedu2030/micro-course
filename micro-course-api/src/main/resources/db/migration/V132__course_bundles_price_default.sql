-- V132__course_bundles_price_default.sql
-- 补充 V51 缺失的 price DEFAULT 0（数据字典要求）

ALTER TABLE course_bundles ALTER COLUMN price SET DEFAULT 0;
