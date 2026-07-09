-- Hermes Webhook: 添加讲述稿字段到 lesson 表
ALTER TABLE lessons ADD COLUMN IF NOT EXISTS script_content TEXT;
COMMENT ON COLUMN lessons.script_content IS '讲述稿/脚本内容，由 Hermes Webhook 推送';
