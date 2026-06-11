-- V7__gate2_records_wrong_questions.sql · 答题记录 + 错题集
-- 依据: docs/数据字典.md v0.5 §4.4-4.5
-- 日期: 2026-06-11

-- 1. exercise_records（答题记录表）
CREATE TABLE exercise_records (
    id           BIGSERIAL PRIMARY KEY,
    exercise_id  BIGINT    NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    user_id      BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    attempt_no   INTEGER   NOT NULL,
    score        INTEGER,
    total_score  INTEGER,
    passed       BOOLEAN,
    duration     INTEGER,
    answers      TEXT,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_er_user_exercise ON exercise_records(user_id, exercise_id);
CREATE INDEX idx_er_exercise       ON exercise_records(exercise_id);

-- 2. wrong_questions（错题集表）
CREATE TABLE wrong_questions (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    question_id  BIGINT    NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    course_id    BIGINT    NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    wrong_count  INTEGER   DEFAULT 1,
    last_wrong_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_wq_user_question UNIQUE (user_id, question_id)
);
CREATE INDEX idx_wq_user_course ON wrong_questions(user_id, course_id);
