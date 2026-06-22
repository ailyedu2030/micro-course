-- V72__fix_question_partial_score_type.sql
-- 修复 questions.partial_score 类型不匹配
-- 前端将该字段存储为规则文本（如 "A=30;B=30"），而原类型为 BOOLEAN
-- 将类型改为 VARCHAR(200) 以兼容前端行为

ALTER TABLE questions ALTER COLUMN partial_score TYPE VARCHAR(200) USING CASE WHEN partial_score IS NULL THEN NULL WHEN partial_score THEN 'true' ELSE 'false' END;

COMMENT ON COLUMN questions.partial_score IS '多选部分给分规则；true/false 或规则文本如"A=30;B=30;C=40"';
