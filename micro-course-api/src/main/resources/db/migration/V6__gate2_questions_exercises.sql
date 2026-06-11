-- V6__gate2_questions_exercises.sql · 题库与练习表
-- 依据: docs/数据字典.md v0.5 §4.1-4.3
-- 日期: 2026-06-11

-- 1. questions（题目表）
CREATE TABLE questions (
    id             BIGSERIAL   PRIMARY KEY,
    course_id      BIGINT      NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    teacher_id     BIGINT      NOT NULL REFERENCES users(id),
    question_type  VARCHAR(20) NOT NULL,
    content        TEXT        NOT NULL,
    options        TEXT,
    answer         TEXT        NOT NULL,
    partial_score  BOOLEAN     DEFAULT FALSE,
    explanation    TEXT,
    difficulty     INTEGER     DEFAULT 1,
    version        INTEGER     NOT NULL DEFAULT 0,
    status         INTEGER     NOT NULL DEFAULT 1,
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_questions_course     ON questions(course_id);
CREATE INDEX idx_questions_teacher    ON questions(teacher_id);
CREATE INDEX idx_questions_type       ON questions(question_type);
CREATE INDEX idx_questions_difficulty ON questions(difficulty);

-- 2. exercises（练习/组卷表）
CREATE TABLE exercises (
    id               BIGSERIAL   PRIMARY KEY,
    chapter_id       BIGINT      REFERENCES course_chapters(id) ON DELETE SET NULL,
    course_id        BIGINT      REFERENCES courses(id) ON DELETE CASCADE,
    title            VARCHAR(200) NOT NULL,
    pass_score       INTEGER     DEFAULT 60,
    time_limit       INTEGER     DEFAULT 0,
    max_attempts     INTEGER     DEFAULT 0,
    show_answer_when VARCHAR(20) DEFAULT 'AFTER_SUBMIT',
    shuffle_questions BOOLEAN    DEFAULT FALSE,
    shuffle_options  BOOLEAN     DEFAULT FALSE,
    total_score      INTEGER     NOT NULL,
    question_count   INTEGER     NOT NULL,
    created_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version          INTEGER     NOT NULL DEFAULT 0
);
CREATE INDEX idx_exercises_chapter ON exercises(chapter_id);
CREATE INDEX idx_exercises_course  ON exercises(course_id);

-- 3. exercise_questions（练习-题目关联表）
CREATE TABLE exercise_questions (
    id           BIGSERIAL PRIMARY KEY,
    exercise_id  BIGINT    NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    question_id  BIGINT    NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    score        INTEGER   NOT NULL,
    sort_order   INTEGER   NOT NULL,
    CONSTRAINT uk_eq_exercise_question UNIQUE (exercise_id, question_id),
    CONSTRAINT uk_eq_exercise_sort     UNIQUE (exercise_id, sort_order)
);
CREATE INDEX idx_eq_exercise ON exercise_questions(exercise_id);
CREATE INDEX idx_eq_question ON exercise_questions(question_id);
