-- V111__course_pricing_review.sql
-- Phase 5: 课程定价 + 审批 + 阶段1分润模型

-- 课程定价字段
ALTER TABLE courses
  ADD COLUMN IF NOT EXISTS list_price          DECIMAL(10,2) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS free_access_scope   VARCHAR(20) NOT NULL DEFAULT 'none',
  ADD COLUMN IF NOT EXISTS free_dept_ids       JSONB DEFAULT '[]'::jsonb,
  ADD COLUMN IF NOT EXISTS discount_scope      VARCHAR(20) NOT NULL DEFAULT 'none',
  ADD COLUMN IF NOT EXISTS discount_percent    INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS pricing_status      VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  ADD COLUMN IF NOT EXISTS pricing_reviewed_at TIMESTAMP,
  ADD COLUMN IF NOT EXISTS pricing_reviewed_by BIGINT REFERENCES users(id);

-- 全局分润率 (阶段1: 单一默认值, 阶段2: 按评级)
CREATE TABLE IF NOT EXISTS platform_share_config (
  id              BIGSERIAL PRIMARY KEY,
  config_key     VARCHAR(50) UNIQUE NOT NULL,
  config_value   VARCHAR(200) NOT NULL,
  description    VARCHAR(200),
  active         BOOLEAN NOT NULL DEFAULT TRUE,
  updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by     BIGINT REFERENCES users(id)
);
INSERT INTO platform_share_config(config_key, config_value, description) VALUES
  ('DEFAULT_SHARE_RATE', '30', '全局默认平台分成率(%)'),
  ('TIER_NEW_RATE', '35', '新教师分成率(%)'),
  ('TIER_BRONZE_RATE', '32', '青铜教师分成率(%)'),
  ('TIER_SILVER_RATE', '28', '白银教师分成率(%)'),
  ('TIER_GOLD_RATE', '25', '黄金教师分成率(%)'),
  ('TIER_PLATINUM_RATE', '20', '铂金教师分成率(%)');

-- CHECK 约束
ALTER TABLE courses ADD CONSTRAINT chk_courses_pricing_status
  CHECK (pricing_status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED'));
ALTER TABLE courses ADD CONSTRAINT chk_courses_free_scope
  CHECK (free_access_scope IN ('none', 'same_department', 'same_college', 'same_school'));
ALTER TABLE courses ADD CONSTRAINT chk_courses_discount_scope
  CHECK (discount_scope IN ('none', 'same_college', 'same_school'));
ALTER TABLE courses ADD CONSTRAINT chk_courses_discount_percent
  CHECK (discount_percent >= 0 AND discount_percent <= 100);
ALTER TABLE courses ADD CONSTRAINT chk_courses_list_price
  CHECK (list_price >= 0);

CREATE INDEX IF NOT EXISTS idx_courses_pricing_status ON courses(pricing_status);
CREATE INDEX IF NOT EXISTS idx_courses_teacher_id ON courses(teacher_id);
