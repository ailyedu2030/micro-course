-- S-004: API Key hash 列，替换明文 api_key 查询
-- 保留 api_key 列（兼容旧代码，后续可清理）
-- SHA-256 输出固定 64 字符 hex

ALTER TABLE users ADD COLUMN api_key_hash VARCHAR(64);

-- 从已有明文 api_key 回填 hash
UPDATE users SET api_key_hash = encode(sha256(api_key::bytea), 'hex') WHERE api_key IS NOT NULL;

-- api_key_hash 唯一约束（同一 key 不应同时分配给多用户）
CREATE UNIQUE INDEX idx_users_api_key_hash ON users(api_key_hash) WHERE api_key_hash IS NOT NULL;

-- 原有明文索引不再需要（查询改为 hash 比对）
DROP INDEX IF EXISTS idx_users_api_key;
