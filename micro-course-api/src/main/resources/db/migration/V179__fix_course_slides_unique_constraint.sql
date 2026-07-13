-- V179: 修复 course_slides 唯一约束，支持多章节独立上传 slide
-- 原约束 uk_slides_course: UNIQUE (course_id) → 一个课程只能有一个 slide
-- 新约束: UNIQUE (course_id, chapter_id) → 每个章节可独立拥有 slide
DROP INDEX IF EXISTS uk_slides_course;
CREATE UNIQUE INDEX IF NOT EXISTS uk_slides_course_chapter ON course_slides(course_id, chapter_id);
