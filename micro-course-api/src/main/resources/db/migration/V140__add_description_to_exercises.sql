-- V140__add_description_to_exercises.sql
-- 为 exercises 表添加 description 字段，支持练习描述的存储与展示
-- 前端 ExerciseForm.vue/ExerciseList.vue 已发送 description 字段

ALTER TABLE exercises ADD COLUMN IF NOT EXISTS description TEXT;

COMMENT ON COLUMN exercises.description IS '练习描述，支持图文说明';
