-- V181: 课件支持按课时(lesson)独立上传，不再按章节(chapter)
-- 原约束：course_slides UNIQUE(course_id, chapter_id)
-- 新约束：course_slides UNIQUE(course_id, lesson_id)
-- 原约束：slide_pages  UNIQUE(course_id, chapter_id, page_number)
-- 新约束：slide_pages  UNIQUE(course_id, lesson_id, page_number)

ALTER TABLE course_slides ADD COLUMN lesson_id BIGINT;
ALTER TABLE slide_pages  ADD COLUMN lesson_id BIGINT;

DROP INDEX IF EXISTS uk_slides_course_chapter;
DROP INDEX IF EXISTS uk_sp_course_chapter_page;
CREATE UNIQUE INDEX IF NOT EXISTS uk_slides_course_lesson ON course_slides(course_id, lesson_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sp_course_lesson_page ON slide_pages(course_id, lesson_id, page_number);

CREATE INDEX IF NOT EXISTS idx_slides_lesson ON course_slides(lesson_id);
CREATE INDEX IF NOT EXISTS idx_sp_lesson ON slide_pages(lesson_id);
