-- V108__course_pricing.sql
-- Phase 4: 课程定价扩展

ALTER TABLE courses ADD COLUMN IF NOT EXISTS
  free_access_scope VARCHAR(20) NOT NULL DEFAULT 'none';

ALTER TABLE courses ADD COLUMN IF NOT EXISTS
  free_dept_ids JSONB DEFAULT '[]'::jsonb;

ALTER TABLE courses ADD COLUMN IF NOT EXISTS
  discount_scope VARCHAR(20) NOT NULL DEFAULT 'none';

ALTER TABLE courses ADD COLUMN IF NOT EXISTS
  discount_percent INTEGER NOT NULL DEFAULT 0;

-- CHECK 约束
ALTER TABLE courses ADD CONSTRAINT chk_course_free_scope
  CHECK (free_access_scope IN ('none', 'same_department', 'same_college', 'same_school'));

ALTER TABLE courses ADD CONSTRAINT chk_course_discount_scope
  CHECK (discount_scope IN ('none', 'same_college', 'same_school'));

ALTER TABLE courses ADD CONSTRAINT chk_course_discount_percent
  CHECK (discount_percent >= 0 AND discount_percent <= 100);
