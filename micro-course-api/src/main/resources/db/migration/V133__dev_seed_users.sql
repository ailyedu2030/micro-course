-- V133__dev_seed_users.sql
-- 开发环境种子用户（已知密码：student123）
-- 仅在开发/演示环境需要，生产环境请跳过此迁移

INSERT INTO users (username, password, real_name, role, status, cas_bound, created_at, updated_at)
SELECT 'teacher', '$2b$12$8INfOluI..wPsed6wvZSsOxfoH/dzsxaXvPR5ABQffWVKyjH7gcmK', '李教授', 'TEACHER', 1, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'teacher');

INSERT INTO users (username, password, real_name, role, status, cas_bound, created_at, updated_at)
SELECT 'student', '$2b$12$8INfOluI..wPsed6wvZSsOxfoH/dzsxaXvPR5ABQffWVKyjH7gcmK', '陈小明', 'STUDENT', 1, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'student');
