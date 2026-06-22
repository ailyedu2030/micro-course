-- =============================================================================
-- V39__fix_operation_logs.sql
-- -----------------------------------------------------------------------------
-- 修复 operation_logs 表缺少的字段
-- 依据：docs/数据字典.md v0.5 §8.2
-- 日期：2026-06-12
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 为 operation_logs 表添加缺失字段
-- -----------------------------------------------------------------------------
ALTER TABLE operation_logs ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64);
ALTER TABLE operation_logs ADD COLUMN IF NOT EXISTS user_agent VARCHAR(500);
ALTER TABLE operation_logs ADD COLUMN IF NOT EXISTS duration_ms INTEGER;
ALTER TABLE operation_logs ADD COLUMN IF NOT EXISTS is_success BOOLEAN;
ALTER TABLE operation_logs ALTER COLUMN action TYPE VARCHAR(100);

-- 添加缺失索引
CREATE INDEX IF NOT EXISTS idx_ol_user ON operation_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_ol_action ON operation_logs(action);
CREATE INDEX IF NOT EXISTS idx_ol_created ON operation_logs(created_at);
CREATE INDEX IF NOT EXISTS idx_ol_target ON operation_logs(target_type, target_id);

COMMENT ON COLUMN operation_logs.trace_id IS '请求追踪 ID（分布式链路追踪）';
COMMENT ON COLUMN operation_logs.user_agent IS '客户端 User-Agent';
COMMENT ON COLUMN operation_logs.duration_ms IS '请求耗时（毫秒）';
COMMENT ON COLUMN operation_logs.is_success IS '是否成功';