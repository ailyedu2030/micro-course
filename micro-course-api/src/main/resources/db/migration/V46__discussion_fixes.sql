-- V46: Discussion module audit fixes
-- P0-1: 评论匿名字段
ALTER TABLE discussion_comments ADD COLUMN IF NOT EXISTS is_anonymous BOOLEAN DEFAULT FALSE;

-- P1: 点赞去重表
CREATE TABLE IF NOT EXISTS discussion_comment_likes (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT    NOT NULL,
    comment_id  BIGINT    NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_comment_like_user UNIQUE (user_id, comment_id)
);

CREATE INDEX IF NOT EXISTS idx_comment_likes_comment ON discussion_comment_likes (comment_id);
