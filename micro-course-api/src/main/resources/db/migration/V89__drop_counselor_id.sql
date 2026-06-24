-- 清理前端孤儿字段 counselor_id
-- 用户决策 C：不需要辅导员独立字段，彻底删除
ALTER TABLE classes DROP CONSTRAINT IF EXISTS fk_classes_counselor;
DROP INDEX IF EXISTS idx_classes_counselor;
ALTER TABLE classes DROP COLUMN IF EXISTS counselor_id;
