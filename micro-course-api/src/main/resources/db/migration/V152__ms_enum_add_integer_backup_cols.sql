-- P1-I-14: MicroSpecialty 系列枚举类型与 DB 不兼容
-- 为 5 个 VARCHAR 状态列添加 INTEGER 备用列，后续迭代完成数据迁移后切换主列
-- 迁移策略：不锁表，分步添加列

-- 1. micro_specialties.status → status_code (INTEGER)
ALTER TABLE micro_specialties ADD COLUMN IF NOT EXISTS status_code INTEGER;

-- 2. micro_specialties.featured_status → featured_status_code (INTEGER)
ALTER TABLE micro_specialties ADD COLUMN IF NOT EXISTS featured_status_code INTEGER;

-- 3. micro_specialties.completion_rule → completion_rule_code (INTEGER)
ALTER TABLE micro_specialties ADD COLUMN IF NOT EXISTS completion_rule_code INTEGER;

-- 4. micro_specialty_enrollments.status → status_code (INTEGER)
ALTER TABLE micro_specialty_enrollments ADD COLUMN IF NOT EXISTS status_code INTEGER;

-- 5. micro_specialty_teachers.role → role_code (INTEGER)
ALTER TABLE micro_specialty_teachers ADD COLUMN IF NOT EXISTS role_code INTEGER;
