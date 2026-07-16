-- V195: 章节级元信息(P1 Stage 1)
-- Trae SKILL.md 模块 3.2 chapter schema 期望字段:
-- no, anchor_point, core_question, chapter_hours

ALTER TABLE course_chapters
  ADD COLUMN IF NOT EXISTS no INT,
  ADD COLUMN IF NOT EXISTS anchor_point TEXT,
  ADD COLUMN IF NOT EXISTS core_question TEXT,
  ADD COLUMN IF NOT EXISTS chapter_hours INT;

-- backfill: no 默认等于 sort_order(交叉审查 P0-1:COALESCE 防止 sort_order NULL 导致 no 仍 NULL)
UPDATE course_chapters
   SET no = COALESCE(sort_order, 0)
 WHERE no IS NULL;

COMMENT ON COLUMN course_chapters.no IS '章号(1-8)';
COMMENT ON COLUMN course_chapters.anchor_point IS '章锚点(贯穿本章的真实故事)';
COMMENT ON COLUMN course_chapters.core_question IS '章核心问题';
COMMENT ON COLUMN course_chapters.chapter_hours IS '章总学时';