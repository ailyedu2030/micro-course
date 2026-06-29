-- V104: Add @Version column to micro_specialty_proposals for OptimisticLockerInnerInterceptor
-- Fixes 500 error on GET /api/micro-specialty-proposals/my

ALTER TABLE micro_specialty_proposals ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;
