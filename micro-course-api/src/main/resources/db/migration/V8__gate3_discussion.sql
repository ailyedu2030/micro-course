-- V8__gate3_discussion.sql · 讨论区表
-- 依据: docs/数据字典.md v0.5 §6.1-6.2
-- 日期: 2026-06-11

-- 1. discussion_posts（讨论区帖子表）
CREATE TABLE discussion_posts (
    id            BIGSERIAL   PRIMARY KEY,
    course_id     BIGINT      REFERENCES courses(id) ON DELETE CASCADE,
    chapter_id    BIGINT      REFERENCES course_chapters(id) ON DELETE CASCADE,
    user_id       BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title         VARCHAR(200) NOT NULL,
    content       TEXT        NOT NULL,
    is_anonymous  BOOLEAN     DEFAULT FALSE,
    is_pinned     BOOLEAN     DEFAULT FALSE,
    is_essence    BOOLEAN     DEFAULT FALSE,
    comment_count INTEGER     DEFAULT 0,
    like_count    INTEGER     DEFAULT 0,
    status        INTEGER     NOT NULL DEFAULT 1,
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_dp_course_chapter ON discussion_posts(course_id, chapter_id);
CREATE INDEX idx_dp_user           ON discussion_posts(user_id);
CREATE INDEX idx_dp_pinned         ON discussion_posts(is_pinned);
CREATE INDEX idx_dp_status         ON discussion_posts(status);

-- 2. discussion_comments（讨论区回复表）
CREATE TABLE discussion_comments (
    id               BIGSERIAL PRIMARY KEY,
    post_id          BIGINT    NOT NULL REFERENCES discussion_posts(id) ON DELETE CASCADE,
    parent_id        BIGINT    REFERENCES discussion_comments(id) ON DELETE CASCADE,
    user_id          BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content          TEXT      NOT NULL,
    is_teacher_reply BOOLEAN   DEFAULT FALSE,
    like_count       INTEGER   DEFAULT 0,
    status           INTEGER   NOT NULL DEFAULT 1,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_dc_post   ON discussion_comments(post_id);
CREATE INDEX idx_dc_parent ON discussion_comments(parent_id);
CREATE INDEX idx_dc_author ON discussion_comments(user_id);
CREATE INDEX idx_dc_status ON discussion_comments(status);
