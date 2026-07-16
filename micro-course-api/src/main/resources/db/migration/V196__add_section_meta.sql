-- V196: 小节级元信息(P1 Stage 1)
-- Trae SKILL.md 模块 3.3 section schema 期望字段:
-- no, learning_objectives, anchor_scenario_step, core_competency, courseware_type, audio_strategy

ALTER TABLE course_sections
  ADD COLUMN IF NOT EXISTS no VARCHAR(20),
  ADD COLUMN IF NOT EXISTS learning_objectives TEXT,  -- JSON 字符串(P1 Stage 1 适配:用 TEXT 而非 JSONB,简化 MyBatis 写入)
  ADD COLUMN IF NOT EXISTS anchor_scenario_step TEXT,
  ADD COLUMN IF NOT EXISTS core_competency VARCHAR(100),
  ADD COLUMN IF NOT EXISTS courseware_type VARCHAR(20) DEFAULT 'HTML',
  ADD COLUMN IF NOT EXISTS audio_strategy VARCHAR(20) DEFAULT '15-segment';

-- backfill: no 默认等于 sort_order(交叉审查 P0-2:COALESCE 防止 sort_order NULL)
UPDATE course_sections
   SET no = COALESCE(sort_order, 0)::VARCHAR
 WHERE no IS NULL;

-- backfill: learning_objectives 默认空数组 JSON
UPDATE course_sections
   SET learning_objectives = '[]'
 WHERE learning_objectives IS NULL;

-- CHECK 约束
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_sections_courseware_type') THEN
    ALTER TABLE course_sections ADD CONSTRAINT chk_sections_courseware_type
      CHECK (courseware_type IS NULL OR courseware_type IN ('HTML', 'PPT', 'BOTH'));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_sections_audio_strategy') THEN
    ALTER TABLE course_sections ADD CONSTRAINT chk_sections_audio_strategy
      CHECK (audio_strategy IS NULL OR audio_strategy IN ('15-segment', '1-merged'));
  END IF;
END $$;

COMMENT ON COLUMN course_sections.no IS '节号(如 1.1, 1.2)';
COMMENT ON COLUMN course_sections.learning_objectives IS '学习目标 JSON 字符串(P1 Stage 1:改为 TEXT 而非 JSONB,简化 MyBatis 写入)';
COMMENT ON COLUMN course_sections.anchor_scenario_step IS '该节锚情境节点';
COMMENT ON COLUMN course_sections.core_competency IS '核心能力';
COMMENT ON COLUMN course_sections.courseware_type IS '课件类型: HTML / PPT / BOTH';
COMMENT ON COLUMN course_sections.audio_strategy IS '音频策略: 15-segment / 1-merged';