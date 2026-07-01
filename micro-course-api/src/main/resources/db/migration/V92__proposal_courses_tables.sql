-- V92__proposal_courses_tables.sql
-- Phase 15: 课程体系动态表 + 负责人近三年主讲课程表
-- 依赖：V91 已扩展主表

-- ============================================================
-- 12.2 proposal_courses — 课程体系动态表
-- ============================================================
CREATE TABLE IF NOT EXISTS proposal_courses (
    id              BIGSERIAL    PRIMARY KEY,
    proposal_id     BIGINT       NOT NULL REFERENCES micro_specialty_proposals(id) ON DELETE CASCADE,
    module_name     VARCHAR(100),
    course_name     VARCHAR(200) NOT NULL,
    hours           INTEGER      DEFAULT 0,
    credits         DECIMAL(4,1) DEFAULT 0.0,
    semester        VARCHAR(50),
    sort_order      INTEGER      DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_pc_proposal ON proposal_courses(proposal_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_pc_unique ON proposal_courses(proposal_id, course_name);

-- ============================================================
-- 12.6 proposal_lead_courses — 负责人近三年主讲课程表
-- ============================================================
CREATE TABLE IF NOT EXISTS proposal_lead_courses (
    id              BIGSERIAL    PRIMARY KEY,
    proposal_id     BIGINT       NOT NULL REFERENCES micro_specialty_proposals(id) ON DELETE CASCADE,
    course_name     VARCHAR(200) NOT NULL,
    credits         DECIMAL(4,1) DEFAULT 0.0,
    hours           INTEGER      DEFAULT 0,
    sort_order      INTEGER      DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_plc_proposal ON proposal_lead_courses(proposal_id);
