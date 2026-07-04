-- O-04: attendance_records 表添加 session_id + user_id 唯一索引
-- 防止重复签到记录，确保幂等性
CREATE UNIQUE INDEX IF NOT EXISTS idx_attendance_unique_session_user
    ON attendance_records(session_id, user_id);
