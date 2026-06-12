-- V24__add_user_teacher_status.sql
-- 为 users 表添加 teacher_status 字段
-- 用于教师入驻审核流程
-- 0=待审核, 1=通过, 2=驳回
-- 日期: 2026-06-12

ALTER TABLE users ADD COLUMN teacher_status INTEGER DEFAULT 0;

COMMENT ON COLUMN users.teacher_status IS '教师入驻审核状态：0=待审核, 1=通过, 2=驳回';