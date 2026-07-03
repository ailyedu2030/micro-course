-- V95__enrollments_add_bundle_id.sql
-- 为 enrollments 表添加 bundle_id 列，支持追溯套餐选课来源

ALTER TABLE enrollments ADD COLUMN IF NOT EXISTS bundle_id BIGINT REFERENCES course_bundles(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_enrollments_bundle ON enrollments(bundle_id);
