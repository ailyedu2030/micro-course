-- V143__add_chapter_id_to_course_slides.sql
-- P0 数据流修复: course_slides 表增加 chapter_id 列,使课件 chapterId 可持久化
-- 参见: V141__add_chapter_id_to_slide_pages.sql (slide_pages 已有 chapter_id)
ALTER TABLE course_slides ADD COLUMN chapter_id BIGINT;
