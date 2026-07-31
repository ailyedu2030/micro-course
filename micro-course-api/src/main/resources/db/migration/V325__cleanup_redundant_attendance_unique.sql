-- =============================================================================
-- V325__cleanup_redundant_attendance_unique.sql
-- -----------------------------------------------------------------------------
-- 【P2 优化 2026-07-30】清理冗余唯一索引/约束
--
-- 背景：
--   V128__attendance_records.sql 已创建 UNIQUE 约束 uk_att_session_user
--      (session_id, user_id)，PostgreSQL 会自动生成同名唯一索引。
--   V135__add_attendance_records_unique.sql 又创建了唯一索引
--      idx_attendance_unique_session_user ON (session_id, user_id)，完全冗余。
--   由于历史原因（V135 在早期版本单独添加时未注意到 V128 已有约束），
--     attendance_records 表存在两套唯一性保护，浪费存储和写入开销。
--
--   V102__orders_partial_unique.sql 和 V165__fix_order_enrollment_defects.sql
--     均用 IF NOT EXISTS 创建 uk_orders_user_course_active，完全相同。
--     V165 是 DBA 审核时发现 102 缺少 COMMENT 而重新提交，实际不重复创建，
--     因 IF NOT EXISTS 确保幂等。此处仅做文档化记录，不修改。
--
-- 兼容性：
--   DROP INDEX IF EXISTS 确保幂等。约束 uk_att_session_user 仍提供唯一性保护。
-- =============================================================================

-- 删除 V135 创建的冗余唯一索引（uk_att_session_user 约束已提供完全相同的保护）
DROP INDEX IF EXISTS idx_attendance_unique_session_user;

COMMENT ON TABLE attendance_records IS '签到记录表 (V325: idx_attendance_unique_session_user 已清理，唯一性由 uk_att_session_user 约束保障)';
