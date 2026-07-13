-- =============================================================================
-- V188: add content_url to course_sections
-- =============================================================================
-- 用途: 记录课时关联的课件 URL，前端可通过此字段直接加载课件
-- =============================================================================

ALTER TABLE course_sections ADD COLUMN IF NOT EXISTS content_url VARCHAR(500);

COMMENT ON COLUMN course_sections.content_url IS '课件 URL（由 Hermes slide 上传或 Section POST/PUT 写入）';
