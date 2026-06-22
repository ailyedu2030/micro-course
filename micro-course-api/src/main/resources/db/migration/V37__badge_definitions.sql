-- V37: badge_definitions — 徽章定义表（系统内置徽章）
CREATE TABLE badge_definitions (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    icon_url VARCHAR(500),
    category VARCHAR(30) NOT NULL DEFAULT 'LEARNING',
    criteria TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_bd_code ON badge_definitions(code);

-- Seed 系统内置徽章定义
INSERT INTO badge_definitions (code, name, description, category, criteria) VALUES
('FIRST_COURSE', '初识课程', '完成第一门课程', 'COURSE', '{"type":"course_count","value":1}'),
('ALL_COURSES', '学满全部', '完成所有报名的课程', 'COURSE', '{"type":"all_courses_completed","value":true}'),
('SEVEN_DAY_STREAK', '连续打卡', '连续学习打卡7天', 'LEARNING', '{"type":"streak_days","value":7}');
