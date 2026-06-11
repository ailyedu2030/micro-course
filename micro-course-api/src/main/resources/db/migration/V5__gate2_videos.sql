-- V5__gate2_videos.sql · 视频管理表
-- 依据: docs/数据字典.md v0.5 §3.2
-- 日期: 2026-06-11

CREATE TABLE videos (
    id            BIGSERIAL   PRIMARY KEY,
    chapter_id    BIGINT      REFERENCES course_chapters(id) ON DELETE SET NULL,
    course_id     BIGINT      NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    title         VARCHAR(200) NOT NULL,
    file_name     VARCHAR(255),
    file_size     BIGINT      DEFAULT 0,
    file_md5      VARCHAR(64),
    mime_type     VARCHAR(100),
    duration      INTEGER     DEFAULT 0,
    url           VARCHAR(500),
    hls_url       VARCHAR(500),
    thumbnail_url VARCHAR(500),
    status        INTEGER     NOT NULL DEFAULT 0,
    progress      INTEGER     DEFAULT 0,
    error_message VARCHAR(500),
    sort_order    INTEGER     NOT NULL DEFAULT 0,
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version       INTEGER     NOT NULL DEFAULT 0
);
CREATE INDEX idx_videos_course    ON videos(course_id);
CREATE INDEX idx_videos_chapter   ON videos(chapter_id);
CREATE INDEX idx_videos_status    ON videos(status);

COMMENT ON TABLE videos IS '视频管理表（status: 0=UPLOADING, 1=TRANSCODING, 2=COMPLETED, 3=FAILED）';
