-- V52: grades 表补充缺失字段
-- Grade entity 中有 submitted_at 和 version 字段但 V21 未创建对应列
-- 这导致 MyBatis-Plus SELECT 时报 "column does not exist" 500 错误

ALTER TABLE grades
    ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN grades.submitted_at IS '学生提交时间';
COMMENT ON COLUMN grades.version IS '乐观锁版本号';
