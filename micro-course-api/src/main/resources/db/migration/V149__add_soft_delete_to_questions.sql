-- P0 修复：为 questions 表添加软删除支持
-- 问题（题库）是课程的核心数据资产，不可物理删除。
-- 软删除保留完整历史记录，支持审计追溯和版本回退。

ALTER TABLE questions ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
CREATE INDEX IF NOT EXISTS idx_questions_deleted_at ON questions(deleted_at);
