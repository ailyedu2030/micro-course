-- V31__video_bookmarks.sql · 视频书签表
-- 依据: docs/数据字典.md v0.5 §5.5

CREATE TABLE IF NOT EXISTS video_bookmarks (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users(id),
    video_id   BIGINT      REFERENCES videos(id),
    position   INTEGER     NOT NULL,
    label      VARCHAR(100),
    note       TEXT,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_vb_user_video ON video_bookmarks(user_id, video_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_vb_unique ON video_bookmarks(user_id, video_id, position);