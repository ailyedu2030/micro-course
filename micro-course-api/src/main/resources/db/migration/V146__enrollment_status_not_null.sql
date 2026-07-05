-- P0-12 修复：enrollments.enrollment_status 添加 NOT NULL 约束和默认值
-- 先修复存量 NULL 值
UPDATE enrollments SET enrollment_status = 'PENDING' WHERE enrollment_status IS NULL;
-- 再添加约束
ALTER TABLE enrollments ALTER COLUMN enrollment_status SET NOT NULL;
ALTER TABLE enrollments ALTER COLUMN enrollment_status SET DEFAULT 'PENDING';
