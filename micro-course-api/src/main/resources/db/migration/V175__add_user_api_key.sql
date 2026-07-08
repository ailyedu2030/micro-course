-- 教师 API Key（每教师独立密钥，供 Hermes / 第三方系统调用 webhook 时认证）
ALTER TABLE users ADD COLUMN api_key VARCHAR(64) UNIQUE;
CREATE INDEX idx_users_api_key ON users(api_key);