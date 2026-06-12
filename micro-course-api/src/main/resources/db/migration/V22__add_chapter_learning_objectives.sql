-- V22__add_chapter_learning_objectives.sql
-- 为 course_chapters 表添加 learning_objectives 字段
-- 存储章节的学习目标/教学要点（JSON 数组格式）
-- 日期: 2026-06-12

ALTER TABLE course_chapters ADD COLUMN learning_objectives TEXT;

COMMENT ON COLUMN course_chapters.learning_objectives IS '章节学习目标，JSON 数组格式，如：["理解基本概念","掌握分析方法","能够实际应用"]';