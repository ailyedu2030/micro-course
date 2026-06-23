-- V84__micro_specialty_proposals_and_audit.sql
-- Phase 14: 微专业申报表 + 置顶审计表
-- 依赖：V82 已建好 micro_specialties（FK 目标）

-- ============================================================
-- 5. micro_specialty_proposals — 微专业申报表
-- ============================================================
CREATE TABLE micro_specialty_proposals (
    id                          BIGSERIAL PRIMARY KEY,
    proposer_id                 BIGINT       NOT NULL REFERENCES users(id)            ON DELETE RESTRICT,
    title                       VARCHAR(200) NOT NULL,
    description                 TEXT,
    offer_department_id         BIGINT       NOT NULL REFERENCES departments(id)      ON DELETE RESTRICT,
    training_objective          TEXT,
    semester                    VARCHAR(20),
    max_students                INTEGER      NOT NULL DEFAULT 0,
    status                      VARCHAR(20)  NOT NULL DEFAULT 'PENDING_REVIEW',
    review_comment              VARCHAR(1000),
    reviewed_by                 BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at                 TIMESTAMP,
    created_micro_specialty_id  BIGINT       REFERENCES micro_specialties(id) ON DELETE SET NULL,
    created_at                  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_msp_proposer ON micro_specialty_proposals(proposer_id);
CREATE INDEX idx_msp_dept     ON micro_specialty_proposals(offer_department_id);
CREATE INDEX idx_msp_status   ON micro_specialty_proposals(status);
CREATE INDEX idx_msp_created  ON micro_specialty_proposals(created_micro_specialty_id);

-- ============================================================
-- 6. micro_specialty_featured_audit — 置顶审计表
-- ============================================================
CREATE TABLE micro_specialty_featured_audit (
    id                  BIGSERIAL PRIMARY KEY,
    micro_specialty_id  BIGINT       NOT NULL REFERENCES micro_specialties(id) ON DELETE CASCADE,
    operator_id         BIGINT       NOT NULL REFERENCES users(id)            ON DELETE RESTRICT,
    action              VARCHAR(20)  NOT NULL,
    before_value        JSONB,
    after_value         JSONB,
    reason              VARCHAR(500),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_msfa_ms       ON micro_specialty_featured_audit(micro_specialty_id);
CREATE INDEX idx_msfa_operator ON micro_specialty_featured_audit(operator_id);
CREATE INDEX idx_msfa_action   ON micro_specialty_featured_audit(action);
CREATE INDEX idx_msfa_created  ON micro_specialty_featured_audit(created_at);
