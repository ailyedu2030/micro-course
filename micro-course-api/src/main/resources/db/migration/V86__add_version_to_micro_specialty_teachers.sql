-- V86__add_version_to_micro_specialty_teachers.sql
-- Phase 14 P0-3/P0-7: 为 micro_specialty_teachers 添加 version 列以支持乐观锁
ALTER TABLE micro_specialty_teachers ADD COLUMN version INTEGER NOT NULL DEFAULT 0;
