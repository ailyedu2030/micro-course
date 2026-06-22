-- V27__user_follows.sql · 用户关注关联表
-- 依据: docs/数据字典.md v0.5 §6.3

CREATE TABLE IF NOT EXISTS user_follows (
    id           BIGSERIAL   PRIMARY KEY,
    follower_id  BIGINT      NOT NULL REFERENCES users(id),
    following_id BIGINT      NOT NULL REFERENCES users(id),
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_uf_follower ON user_follows(follower_id);
CREATE INDEX IF NOT EXISTS idx_uf_following ON user_follows(following_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_uf_unique ON user_follows(follower_id, following_id);