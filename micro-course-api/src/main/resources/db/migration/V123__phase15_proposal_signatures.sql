-- V123__phase15_proposal_signatures.sql
-- Phase 15 微专业申请表 — 三级签字+共享单位签字子表扩展
-- 说明：基表由 V94 创建，此迁移补充 Phase 15 新增字段

ALTER TABLE proposal_signatures
    ADD COLUMN IF NOT EXISTS shared_unit_id      BIGINT,
    ADD COLUMN IF NOT EXISTS opinion             TEXT,
    ADD COLUMN IF NOT EXISTS sort_order          INTEGER   DEFAULT 0,
    ADD COLUMN IF NOT EXISTS created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE proposal_signatures
    ALTER COLUMN sign_date TYPE DATE USING sign_date::date;

CREATE INDEX IF NOT EXISTS idx_ps_level    ON proposal_signatures(proposal_id, sign_level);
