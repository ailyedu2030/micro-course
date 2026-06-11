-- =============================================================================
-- V2__operation_logs.sql
-- -----------------------------------------------------------------------------
-- 操作日志表（Phase 1 够用）
-- 范围：登录/登出、账户状态变更、部门/专业/班级 CRUD
-- =============================================================================

-- -----------------------------------------------------------------------------
-- operation_logs（操作日志表）
-- -----------------------------------------------------------------------------
CREATE TABLE operation_logs (
    id BIGSERIAL       PRIMARY KEY,
    user_id     BIGINT,
    action      VARCHAR(50)     NOT NULL,
    target_type VARCHAR(30),
    target_id   BIGINT,
    detail      TEXT,
    ip VARCHAR(50),
    success     BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_operation_logs_user_id    ON operation_logs (user_id);
CREATE INDEX idx_operation_logs_action      ON operation_logs (action);
CREATE INDEX idx_operation_logs_created_at  ON operation_logs (created_at);
CREATE INDEX idx_operation_logs_target ON operation_logs (target_type, target_id);

COMMENT ON TABLE  operation_logs IS '操作日志表';
COMMENT ON COLUMN operation_logs.action IS 'LOGIN/LOGOUT/STATUS_CHANGE/CREATE/UPDATE/DELETE';
COMMENT ON COLUMN operation_logs.target_type IS 'USER/DEPARTMENT/MAJOR/CLASS';
COMMENT ON COLUMN operation_logs.detail      IS 'JSON: {"field":"status","old":1,"new":2}';