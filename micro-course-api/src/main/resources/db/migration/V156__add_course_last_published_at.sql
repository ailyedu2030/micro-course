-- V156: 课程管理域 S3 修复
-- 为 courses 表添加 last_published_at 字段，记录课程最近一次发布时间
-- publish() 校验 CLOSED→PUBLISHED 时要求此前必须曾经发布过 (lastPublishedAt != null)
-- 防止 DRAFT→CLOSED 或 REJECTED→CLOSED 后的课程绕过审核直接发布

ALTER TABLE courses ADD COLUMN IF NOT EXISTS last_published_at TIMESTAMP;

COMMENT ON COLUMN courses.last_published_at IS '最近一次发布时间。CLOSED→PUBLISHED 校验依赖此字段';

-- 为已有 PUBLISHED 课程回填 last_published_at = published_at
UPDATE courses SET last_published_at = published_at
WHERE status = 4 AND last_published_at IS NULL AND published_at IS NOT NULL;