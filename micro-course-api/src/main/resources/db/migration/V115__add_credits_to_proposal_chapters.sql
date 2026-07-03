-- V115__add_credits_to_proposal_chapters.sql
-- P1-I-1: 为 proposal_chapters 表添加 credits 列（章节级别学分，用于学时汇总校验）
-- 与 proposal_courses.credits（课程级别学分）互为补充

ALTER TABLE proposal_chapters
    ADD COLUMN IF NOT EXISTS credits DECIMAL(4,1) DEFAULT 0;
