-- V120__phase15_proposal_fields.sql
-- Phase 15 微专业申请表 — 主表扩展
-- 依赖：V84 已建好 micro_specialty_proposals（基表）

ALTER TABLE micro_specialty_proposals
    ADD COLUMN IF NOT EXISTS type                      VARCHAR(20) NOT NULL DEFAULT '急需紧缺型',
    ADD COLUMN IF NOT EXISTS university_full_name      VARCHAR(200),
    ADD COLUMN IF NOT EXISTS contact_person_name       VARCHAR(50),
    ADD COLUMN IF NOT EXISTS contact_phone             VARCHAR(20),
    ADD COLUMN IF NOT EXISTS contact_email             VARCHAR(100),
    ADD COLUMN IF NOT EXISTS construction_start_year   INTEGER,
    ADD COLUMN IF NOT EXISTS construction_end_year     INTEGER,
    ADD COLUMN IF NOT EXISTS total_hours               INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS target_audience           TEXT,
    ADD COLUMN IF NOT EXISTS background_significance   TEXT,
    ADD COLUMN IF NOT EXISTS training_features         TEXT,
    ADD COLUMN IF NOT EXISTS quality_assurance         TEXT,
    ADD COLUMN IF NOT EXISTS expected_outcomes         TEXT,
    ADD COLUMN IF NOT EXISTS additional_notes          TEXT,
    ADD COLUMN IF NOT EXISTS validation_passed         BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS last_auto_saved_at        TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_msp_type ON micro_specialty_proposals(type);
CREATE INDEX IF NOT EXISTS idx_msp_status_type ON micro_specialty_proposals(status, type);
