-- V194: 回滚 - 删除课程级元信息字段
-- 用于紧急回滚 (生产安全铁律要求：每次 migration 必须有回滚路径)

DROP INDEX IF EXISTS uk_courses_hid;

ALTER TABLE courses
  DROP COLUMN IF EXISTS evaluation_scheme,
  DROP COLUMN IF EXISTS learning_mode,
  DROP COLUMN IF EXISTS teaching_philosophy,
  DROP COLUMN IF EXISTS total_weeks,
  DROP COLUMN IF EXISTS total_hours,
  DROP COLUMN IF EXISTS hid;
