-- V44__grades_unique_index.sql
-- MISC-NEW-1: grades 表加部分唯一索引,防止同一学生同一练习同一次 attempt 产生重复成绩
CREATE UNIQUE INDEX IF NOT EXISTS uk_grade_user_exercise_attempt
    ON grades (user_id, exercise_id, attempt_no)
    WHERE deleted_at IS NULL;