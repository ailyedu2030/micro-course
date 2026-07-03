-- V121__phase15_proposal_courses.sql
-- Phase 15 微专业申请表 — 课程体系子表扩展
-- 说明：基表由 V92 创建，此迁移补充 created_at / updated_at 时间戳列

ALTER TABLE proposal_courses
    ADD COLUMN IF NOT EXISTS created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
