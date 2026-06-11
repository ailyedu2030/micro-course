-- Badges table for milestone achievements
CREATE TABLE badges (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    badge_type VARCHAR(50) NOT NULL,
    badge_name VARCHAR(100) NOT NULL,
    earned_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP DEFAULT NULL
);

CREATE INDEX idx_badges_user_id ON badges(user_id);
CREATE INDEX idx_badges_badge_type ON badges(badge_type);
CREATE UNIQUE INDEX idx_badges_user_type ON badges(user_id, badge_type);
