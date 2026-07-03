-- V122__phase15_proposal_team_members.sql
-- Phase 15 微专业申请表 — 教学团队成员子表扩展
-- 说明：基表由 V93 创建，此迁移补充 Phase 15 新增字段

ALTER TABLE proposal_team_members
    ADD COLUMN IF NOT EXISTS seq_no            INTEGER,
    ADD COLUMN IF NOT EXISTS institution       VARCHAR(200),
    ADD COLUMN IF NOT EXISTS major             VARCHAR(200),
    ADD COLUMN IF NOT EXISTS previously_taught TEXT,
    ADD COLUMN IF NOT EXISTS to_teach          TEXT,
    ADD COLUMN IF NOT EXISTS sort_order        INTEGER   DEFAULT 0,
    ADD COLUMN IF NOT EXISTS created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_ptm_sort ON proposal_team_members(proposal_id, sort_order);
