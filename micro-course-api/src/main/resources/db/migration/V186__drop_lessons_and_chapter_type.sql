-- V186: 清理遗留表和字段
-- 所有数据已迁移到 course_sections，安全删除

ALTER TABLE course_chapters DROP COLUMN IF EXISTS chapter_type;
DROP TABLE IF EXISTS lessons CASCADE;

-- 注意：section_id 为空的 slide 需要在应用层处理后单独清理
-- 此处不自动删除，避免生产数据丢失
