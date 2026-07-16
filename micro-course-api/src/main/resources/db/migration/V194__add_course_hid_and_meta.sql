-- V194: 课程级元信息(P1 Stage 1)
-- Trae SKILL.md 模块 3.1 course schema 期望字段:
-- hid, total_hours, total_weeks, teaching_philosophy, learning_mode, evaluation_scheme

ALTER TABLE courses
  ADD COLUMN IF NOT EXISTS hid VARCHAR(64),
  ADD COLUMN IF NOT EXISTS total_hours INT,
  ADD COLUMN IF NOT EXISTS total_weeks INT,
  ADD COLUMN IF NOT EXISTS teaching_philosophy TEXT,
  ADD COLUMN IF NOT EXISTS learning_mode VARCHAR(50),
  ADD COLUMN IF NOT EXISTS evaluation_scheme TEXT;

-- backfill: 已有课程生成 legacy hid
UPDATE courses
   SET hid = 'legacy-' || id
 WHERE hid IS NULL;

-- 唯一索引(允许 NULL,但非 NULL 必须唯一)
CREATE UNIQUE INDEX IF NOT EXISTS uk_courses_hid ON courses(hid) WHERE hid IS NOT NULL;

-- 字段注释
COMMENT ON COLUMN courses.hid IS '全局唯一 ID(Hermes 同步用)';
COMMENT ON COLUMN courses.total_hours IS '总学时';
COMMENT ON COLUMN courses.total_weeks IS '总教学周数';
COMMENT ON COLUMN courses.teaching_philosophy IS '教学理念 JSON 数组或文本';
COMMENT ON COLUMN courses.learning_mode IS '学习模式: online-self-study / offline-blended / hybrid';
COMMENT ON COLUMN courses.evaluation_scheme IS '考核方案文本';