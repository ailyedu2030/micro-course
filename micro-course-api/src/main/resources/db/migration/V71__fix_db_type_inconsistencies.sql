-- V71__fix_db_type_inconsistencies.sql
-- 修复 R2 全量审查发现的 DB 类型/索引不一致问题
-- 依据: docs/数据字典.md v0.5 + 交叉验证 R2 审查
-- 日期: 2026-06-22

-- ---------------------------------------------------------------------------
-- 修复1: operation_logs.target_type 长度 VARCHAR(30)→VARCHAR(50)
-- V2 建表为 VARCHAR(30), 数据字典要求 VARCHAR(50), V39 仅改了 action 长度
-- ---------------------------------------------------------------------------
ALTER TABLE operation_logs ALTER COLUMN target_type TYPE VARCHAR(50);

-- ---------------------------------------------------------------------------
-- 修复2: 删除 V2 创建的重复索引（已被 V39 的同功能索引替代）
-- V2 创建 idx_operation_logs_user_id/action/created_at/target
-- V39 创建 idx_ol_user/action/created/target（同名功能，保留 V39 新版）
-- ---------------------------------------------------------------------------
DROP INDEX IF EXISTS idx_operation_logs_user_id;
DROP INDEX IF EXISTS idx_operation_logs_action;
DROP INDEX IF EXISTS idx_operation_logs_created_at;
DROP INDEX IF EXISTS idx_operation_logs_target;

-- ---------------------------------------------------------------------------
-- 修复3: 类型统一 SMALLINT → INTEGER
-- 数据字典全部使用 INTEGER, 但以下表使用了 SMALLINT
-- ---------------------------------------------------------------------------
-- teaching_classes.status
ALTER TABLE teaching_classes ALTER COLUMN status TYPE INTEGER;
-- course_review_logs.previous_status / new_status
ALTER TABLE course_review_logs ALTER COLUMN previous_status TYPE INTEGER;
ALTER TABLE course_review_logs ALTER COLUMN new_status TYPE INTEGER;
-- course_reviews.rating
ALTER TABLE course_reviews ALTER COLUMN rating TYPE INTEGER;
