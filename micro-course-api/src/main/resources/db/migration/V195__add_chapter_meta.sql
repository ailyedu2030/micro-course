-- V195: 章节级元信息(P1 Stage 1)
-- Trae SKILL.md 模块 3.2 chapter schema 期望字段:
-- no, anchor_point, core_question, chapter_hours

ALTER TABLE course_chapters
  ADD COLUMN IF NOT EXISTS no INT,
  ADD COLUMN IF NOT EXISTS anchor_point TEXT,
  ADD COLUMN IF NOT EXISTS core_question TEXT,
  ADD COLUMN IF NOT EXISTS chapter_hours INT;

-- backfill: no 默认等于 sort_order
UPDATE course_chapters
   SET no = sort_order
 WHERE no IS NULL;

COMMENT ON COLUMN course_chapters.no IS '章号(1-8)';
COMMENT ON COLUMN course_chapters.anchor_point IS '章锚点(贯穿本章的真实故事)';
COMMENT ON COLUMN course_chapters.core_question IS '章核心问题';
COMMENT ON COLUMN course_chapters.chapter_hours IS '章总学时';