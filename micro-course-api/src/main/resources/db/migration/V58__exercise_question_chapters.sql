-- V58__exercise_question_chapters.sql
-- 练习/题目多章节支持 + 综合题类型

-- 练习-章节关联表
CREATE TABLE IF NOT EXISTS exercise_chapters (
    exercise_id BIGINT NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    chapter_id BIGINT NOT NULL REFERENCES course_chapters(id) ON DELETE CASCADE,
    PRIMARY KEY (exercise_id, chapter_id)
);

-- 题目-章节关联表
CREATE TABLE IF NOT EXISTS question_chapters (
    question_id BIGINT NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    chapter_id BIGINT NOT NULL REFERENCES course_chapters(id) ON DELETE CASCADE,
    PRIMARY KEY (question_id, chapter_id)
);

CREATE INDEX IF NOT EXISTS idx_exercise_chapters_exercise ON exercise_chapters(exercise_id);
CREATE INDEX IF NOT EXISTS idx_exercise_chapters_chapter ON exercise_chapters(chapter_id);
CREATE INDEX IF NOT EXISTS idx_question_chapters_question ON question_chapters(question_id);
CREATE INDEX IF NOT EXISTS idx_question_chapters_chapter ON question_chapters(chapter_id);
