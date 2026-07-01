-- V96__add_micro_specialty_name.sql
-- P0-4 修复：添加微专业名称列（独立于 title 申报高校名称）
ALTER TABLE micro_specialty_proposals
    ADD COLUMN IF NOT EXISTS micro_specialty_name VARCHAR(200);
