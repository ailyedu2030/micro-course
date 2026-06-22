-- V30__course_notes.sql · 课程笔记表
-- 依据: docs/数据字典.md v0.5 §5.4

CREATE TABLE IF NOT EXISTS course_notes (
    id            BIGSERIAL   PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES users(id),
    course_id     BIGINT      NOT NULL REFERENCES courses(id),
    chapter_id    BIGINT,
    video_id      BIGINT,
    video_position INTEGER,
    title         VARCHAR(200) NOT NULL,
    content       TEXT        NOT NULL,
    is_public     BOOLEAN     NOT NULL DEFAULT false,
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cn_chapter ON course_notes(chapter_id);
CREATE INDEX IF NOT EXISTS idx_cn_video ON course_notes(video_id);
CREATE INDEX IF NOT EXISTS idx_cn_user_course ON course_notes(user_id, course_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_cn_unique ON course_notes(user_id, course_id, chapter_id, video_id) NULLS NOT DISTINCT;