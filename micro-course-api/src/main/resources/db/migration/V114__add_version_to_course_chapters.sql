-- V114__add_version_to_course_chapters.sql
-- P0-4 修复: V109 建 micro_specialty_course_chapters 时未创建 version 列
-- 但 Entity 有 @Version 注解，导致该表完全不可写入。
ALTER TABLE micro_specialty_course_chapters ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;
