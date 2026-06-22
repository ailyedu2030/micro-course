-- V53__lessons_table.sql
-- Phase 0: 教师工作台基础设施
-- 章节→课时层次结构
-- 迁移：videos.chapterId → lessons, exercises.chapterId → lessons

CREATE TABLE IF NOT EXISTS lessons (
    id BIGSERIAL PRIMARY KEY,
    chapter_id BIGINT NOT NULL REFERENCES course_chapters(id),
    course_id BIGINT NOT NULL REFERENCES courses(id),
    title VARCHAR(200) NOT NULL,
    lesson_type VARCHAR(20) NOT NULL DEFAULT 'VIDEO',
    sort_order INTEGER NOT NULL DEFAULT 0,
    duration INTEGER NOT NULL DEFAULT 0,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_lessons_chapter ON lessons(chapter_id);
CREATE INDEX IF NOT EXISTS idx_lessons_course ON lessons(course_id);
CREATE INDEX IF NOT EXISTS idx_lessons_type ON lessons(lesson_type);
CREATE INDEX IF NOT EXISTS idx_lessons_sort ON lessons(chapter_id, sort_order);

-- 向 course_slides 添加 lesson_id（可空，向后兼容）
ALTER TABLE course_slides ADD COLUMN IF NOT EXISTS lesson_id BIGINT REFERENCES lessons(id);
CREATE INDEX IF NOT EXISTS idx_slides_lesson ON course_slides(lesson_id);
