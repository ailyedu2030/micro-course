-- V93__proposal_team_members.sql
-- Phase 15: 教学团队成员动态表
-- 依赖：V91 已扩展主表

-- ============================================================
-- 12.3 proposal_team_members — 教学团队成员动态表
-- ============================================================
CREATE TABLE IF NOT EXISTS proposal_team_members (
    id              BIGSERIAL    PRIMARY KEY,
    proposal_id     BIGINT       NOT NULL REFERENCES micro_specialty_proposals(id) ON DELETE CASCADE,
    member_type     VARCHAR(20)  NOT NULL DEFAULT 'MEMBER',
    seq             INTEGER      DEFAULT 0,
    name            VARCHAR(50)  NOT NULL,
    age             INTEGER,
    title           VARCHAR(50),
    organization    VARCHAR(200),
    profession      VARCHAR(200),
    taught_courses  TEXT,
    planned_courses TEXT
);

CREATE INDEX IF NOT EXISTS idx_ptm_proposal ON proposal_team_members(proposal_id);
CREATE INDEX IF NOT EXISTS idx_ptm_type     ON proposal_team_members(member_type);
