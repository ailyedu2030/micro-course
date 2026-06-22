-- V38: achievements — 用户成就表（替代 V18 单表 badges）
CREATE TABLE achievements (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    badge_code VARCHAR(50) NOT NULL,
    badge_name VARCHAR(100) NOT NULL,
    earned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP DEFAULT NULL
);

CREATE UNIQUE INDEX idx_ach_user_badge ON achievements(user_id, badge_code);
CREATE INDEX idx_ach_user_id ON achievements(user_id);
