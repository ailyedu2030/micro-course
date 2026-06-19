-- V45__add_lesson_id_to_learning_progress.sql
-- Phase 5 super-fix: 修复进度模型错位 (P0-2)
-- 在 learning_progress 增加 lesson_id,使进度跟踪粒度从 chapter 级细化到 lesson/video 级
-- 旧记录 chapter_id + lesson_id=NULL 表示整章粒度,保持向后兼容

ALTER TABLE learning_progress
    ADD COLUMN lesson_id BIGINT REFERENCES videos(id) ON DELETE CASCADE;

ALTER TABLE learning_progress DROP CONSTRAINT IF EXISTS uk_lp_user_chapter;
CREATE UNIQUE INDEX IF NOT EXISTS uk_lp_user_lesson
    ON learning_progress (user_id, course_id, lesson_id)
    WHERE lesson_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_lp_user_course_lesson
    ON learning_progress(user_id, course_id, lesson_id);
