-- V90: 购物车表 (P2-16: 购物车服务端同步)
-- 替代纯 localStorage 方案，支持多设备/多标签页同步

CREATE TABLE IF NOT EXISTS cart_items (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    course_id BIGINT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    quantity INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    UNIQUE (user_id, course_id, deleted_at)
);

CREATE INDEX IF NOT EXISTS idx_cart_user ON cart_items(user_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_cart_course ON cart_items(course_id) WHERE deleted_at IS NULL;
