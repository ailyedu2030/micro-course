-- V124__phase15_proposal_shared_units.sql
-- Phase 15 微专业申请表 — 共建共享单位子表扩展
-- 说明：基表由 V94 创建，此迁移补充时间戳列

ALTER TABLE proposal_shared_units
    ADD COLUMN IF NOT EXISTS created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_psu_sort ON proposal_shared_units(proposal_id, sort_order);
