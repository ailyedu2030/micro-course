-- learning_progress.lesson_id 原 FK 指向已删除的 lessons 表
-- V186 后 lesson_id 实际存储 course_sections.id，不再有外键约束
ALTER TABLE learning_progress DROP CONSTRAINT IF EXISTS learning_progress_lesson_id_fkey;
