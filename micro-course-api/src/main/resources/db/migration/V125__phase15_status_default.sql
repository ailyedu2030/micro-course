-- V125__phase15_status_default.sql
-- Phase 15 微专业申请表 — 状态默认值变更
-- 仅对新行生效，旧数据 status 保持不变

ALTER TABLE micro_specialty_proposals
    ALTER COLUMN status SET DEFAULT 'DRAFT';
