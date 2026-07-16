-- V196: 回滚 - 删除小节级元信息字段
-- 用于紧急回滚 (生产安全铁律要求：每次 migration 必须有回滚路径)

ALTER TABLE course_sections
  DROP CONSTRAINT IF EXISTS chk_sections_audio_strategy,
  DROP CONSTRAINT IF EXISTS chk_sections_courseware_type;

ALTER TABLE course_sections
  DROP COLUMN IF EXISTS audio_strategy,
  DROP COLUMN IF EXISTS courseware_type,
  DROP COLUMN IF EXISTS core_competency,
  DROP COLUMN IF EXISTS anchor_scenario_step,
  DROP COLUMN IF EXISTS learning_objectives,
  DROP COLUMN IF EXISTS no;
