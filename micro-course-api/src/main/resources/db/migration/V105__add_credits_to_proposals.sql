-- V105: Add credits column to micro_specialty_proposals
ALTER TABLE micro_specialty_proposals ADD COLUMN IF NOT EXISTS credits NUMERIC(5,1);
