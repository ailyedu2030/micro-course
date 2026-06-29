-- V106: Add version column to micro_specialty_courses for @Version optimistic lock
ALTER TABLE micro_specialty_courses ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;
