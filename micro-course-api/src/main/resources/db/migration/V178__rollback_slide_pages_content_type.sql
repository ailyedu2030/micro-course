-- V178: 回滚 V177 - 删除 content_type 和 html_content 字段
-- 用于紧急回滚 (生产安全铁律要求：每次 migration 必须有回滚路径)

ALTER TABLE slide_pages DROP CONSTRAINT IF EXISTS chk_slide_pages_content_type;

DROP INDEX IF EXISTS idx_slide_pages_content_type;

ALTER TABLE slide_pages DROP COLUMN IF EXISTS content_type;
ALTER TABLE slide_pages DROP COLUMN IF EXISTS html_content;
