-- V82: MicroSpecialty feature placeholder (feature removed, retained for existing test DB compatibility)
-- Original migration removed as MicroSpecialty feature was deprecated
-- This no-op ensures Flyway checksum matches expected value for any DBs that previously applied V82
SELECT 1 WHERE 1=0;
