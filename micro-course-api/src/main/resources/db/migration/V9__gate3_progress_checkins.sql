-- V9__gate3_progress_checkins.sql · 学习进度+打卡表
-- 依据: docs/数据字典.md v0.5 §5.1-5.2
-- 日期: 2026-06-11

-- 1. learning_progress（学习进度表）
CREATE TABLE learning_progress (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    course_id          BIGINT    NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    chapter_id         BIGINT    REFERENCES course_chapters(id) ON DELETE CASCADE,
    video_progress     INTEGER   DEFAULT 0,
    video_position     INTEGER   DEFAULT 0,
    exercise_completed BOOLEAN   DEFAULT FALSE,
    exercise_passed    BOOLEAN   DEFAULT FALSE,
    total_watch_time   INTEGER   DEFAULT 0,
    device_id          VARCHAR(100),
    platform           VARCHAR(20),
    playback_speed     DOUBLE PRECISION DEFAULT 1.0,
    confidence         INTEGER   DEFAULT 0,
    completed          BOOLEAN   DEFAULT FALSE,
    last_watch_at      TIMESTAMP,
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_lp_user_chapter UNIQUE (user_id, chapter_id)
);
CREATE INDEX idx_lp_user_course ON learning_progress(user_id, course_id);

-- 2. check_ins（学习打卡表）
CREATE TABLE check_ins (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    checkin_date DATE      NOT NULL,
    duration     INTEGER   DEFAULT 0,
    streak_days  INTEGER   DEFAULT 0,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ci_user_date UNIQUE (user_id, checkin_date)
);
CREATE INDEX idx_ci_user ON check_ins(user_id);
