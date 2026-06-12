-- V23__add_course_tags.sql
-- 为 courses 表添加 tags 字段（JSON 数组格式）
-- 存储课程标签，如：["Python", "数据分析", "机器学习"]
-- 日期: 2026-06-12

ALTER TABLE courses ADD COLUMN tags TEXT;

COMMENT ON COLUMN courses.tags IS '课程标签，JSON 数组格式，如：["Python","数据分析"]';