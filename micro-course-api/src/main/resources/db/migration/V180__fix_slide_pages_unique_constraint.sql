-- V180: 修复 slide_pages 唯一约束，支持多章节独立上传 slide
-- 原约束 uk_sp_course_page: UNIQUE (course_id, page_number) → 多课时上传 page_number=1 时冲突
-- 新约束: UNIQUE (course_id, chapter_id, page_number)
DROP INDEX IF EXISTS uk_sp_course_page;
CREATE UNIQUE INDEX IF NOT EXISTS uk_sp_course_chapter_page ON slide_pages(course_id, chapter_id, page_number);
