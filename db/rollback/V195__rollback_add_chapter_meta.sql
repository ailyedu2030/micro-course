-- V195: 回滚 - 删除章节级元信息字段
-- 用于紧急回滚 (生产安全铁律要求：每次 migration 必须有回滚路径)

ALTER TABLE course_chapters
  DROP COLUMN IF EXISTS chapter_hours,
  DROP COLUMN IF EXISTS core_question,
  DROP COLUMN IF EXISTS anchor_point,
  DROP COLUMN IF EXISTS no;
