-- =============================================================================
-- V127__chapter_offline_sessions.sql
-- -----------------------------------------------------------------------------
-- 线下课排期表
-- 每个 OFFLINE 章节可以有一个或多个排期（如每周三 14:00-15:30 教学楼301）
-- =============================================================================

CREATE TABLE IF NOT EXISTS chapter_offline_sessions (
    id              BIGSERIAL       PRIMARY KEY,
    chapter_id      BIGINT          NOT NULL REFERENCES course_chapters(id) ON DELETE CASCADE,
    session_date    DATE            NOT NULL,
    start_time      TIME            NOT NULL,
    end_time        TIME            NOT NULL,
    location        VARCHAR(200),
    teacher_notes   TEXT,
    sort_order      INTEGER         DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         INTEGER         NOT NULL DEFAULT 0,
    deleted_at      TIMESTAMP
);

CREATE INDEX idx_cos_chapter ON chapter_offline_sessions(chapter_id);

COMMENT ON TABLE  chapter_offline_sessions IS '线下课排期表';
COMMENT ON COLUMN chapter_offline_sessions.chapter_id IS '所属章节 FK';
COMMENT ON COLUMN chapter_offline_sessions.session_date IS '上课日期';
COMMENT ON COLUMN chapter_offline_sessions.start_time IS '上课时间';
COMMENT ON COLUMN chapter_offline_sessions.end_time IS '下课时间';
COMMENT ON COLUMN chapter_offline_sessions.location IS '上课地点（自由文本）';
COMMENT ON COLUMN chapter_offline_sessions.teacher_notes IS '教师备注/课前通知';
