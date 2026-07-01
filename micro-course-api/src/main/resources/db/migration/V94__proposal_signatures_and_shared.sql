-- V94__proposal_signatures_and_shared.sql
-- Phase 15: 签字盖章记录表 + 共建共享单位表
-- 依赖：V91 已扩展主表

-- ============================================================
-- 12.4 proposal_signatures — 签字盖章记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS proposal_signatures (
    id                  BIGSERIAL    PRIMARY KEY,
    proposal_id         BIGINT       NOT NULL REFERENCES micro_specialty_proposals(id) ON DELETE CASCADE,
    sign_level          VARCHAR(20)  NOT NULL,
    unit_seq            INTEGER      DEFAULT 0,
    opinion_text        TEXT,
    signature_type      VARCHAR(20)  DEFAULT 'TEXT',
    signature_text      VARCHAR(100),
    signature_image_url VARCHAR(500),
    seal_image_url      VARCHAR(500),
    sign_date           TIMESTAMP,
    remark              VARCHAR(200)
);

CREATE INDEX IF NOT EXISTS idx_ps_proposal ON proposal_signatures(proposal_id);
CREATE INDEX IF NOT EXISTS idx_ps_level    ON proposal_signatures(sign_level);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ps_unique ON proposal_signatures(proposal_id, sign_level, unit_seq);

-- ============================================================
-- 12.5 proposal_shared_units — 共建共享单位表
-- ============================================================
CREATE TABLE IF NOT EXISTS proposal_shared_units (
    id              BIGSERIAL    PRIMARY KEY,
    proposal_id     BIGINT       NOT NULL REFERENCES micro_specialty_proposals(id) ON DELETE CASCADE,
    unit_name       VARCHAR(200) NOT NULL,
    unit_type       VARCHAR(20)  NOT NULL,
    sort_order      INTEGER      DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_psu_proposal ON proposal_shared_units(proposal_id);
CREATE INDEX IF NOT EXISTS idx_psu_type     ON proposal_shared_units(unit_type);
