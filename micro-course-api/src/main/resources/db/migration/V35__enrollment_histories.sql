-- =============================================================================
-- V35__enrollment_histories.sql
-- -----------------------------------------------------------------------------
-- 选课变更历史表
-- 范围：enrollment_histories
-- 依据：docs/数据字典.md v0.5 §2.14
-- 日期：2026-06-12
-- =============================================================================

-- -----------------------------------------------------------------------------
-- enrollment_histories（选课变更历史表）
-- 记录选课状态变更的完整审计轨迹。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS enrollment_histories (
    id              BIGSERIAL       PRIMARY KEY,
    enrollment_id   BIGINT          NOT NULL REFERENCES enrollments(id) ON DELETE CASCADE,
    previous_status VARCHAR(20),
    new_status      VARCHAR(20)    NOT NULL,
    reason          VARCHAR(500),
    operator_id     BIGINT          REFERENCES users(id) ON DELETE SET NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_eh_enrollment ON enrollment_histories(enrollment_id);
CREATE INDEX idx_eh_operator   ON enrollment_histories(operator_id);

COMMENT ON TABLE  enrollment_histories IS '选课变更历史表';
COMMENT ON COLUMN enrollment_histories.previous_status IS 'PENDING / APPROVED / WAITLIST / CANCELLED / REJECTED';
COMMENT ON COLUMN enrollment_histories.new_status IS 'PENDING / APPROVED / WAITLIST / CANCELLED / REJECTED';