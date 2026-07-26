-- P1-C 修复: original_name 应 NOT NULL (数据字典 v0.5 §3.1)
-- V5 建表时无 NOT NULL, V25 重命名后仍未补充
-- 先回填 NULL 值为空字符串, 再加约束
UPDATE videos SET original_name = '' WHERE original_name IS NULL;
ALTER TABLE videos ALTER COLUMN original_name SET NOT NULL;
