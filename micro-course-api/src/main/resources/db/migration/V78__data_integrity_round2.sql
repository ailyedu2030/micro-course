-- V78__data_integrity_round2.sql · 数据完整性第二轮加固
-- 日期: 2026-06-23
-- 1. enrollments.completed 默认 FALSE（应用层判断学习进度，此字段做冗余）
-- 2. courses.deleted_at 与 status=ARCHIVED 一致性（软删除应同步）
-- 3. 增强索引：enrollments(user_id, status)（高频查询已选课列表）
-- 4. 增强索引：notifications(created_at DESC)（通知列表按时间倒序）
-- 5. 修复 orders 表 order_no 格式（V50 已加 UK，无需新增）

-- 1. enrollments.completed NOT NULL 约束（V53/V61 已有但可能未设 NOT NULL）
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'enrollments' AND column_name = 'completed'
    ) THEN
        UPDATE enrollments SET completed = FALSE WHERE completed IS NULL;
        ALTER TABLE enrollments ALTER COLUMN completed SET NOT NULL;
        ALTER TABLE enrollments ALTER COLUMN completed SET DEFAULT FALSE;
    END IF;
END $$;

-- 2. courses 软删除字段 NOT NULL 约束（V16 已加 deleted_at）
DO $$
BEGIN
    UPDATE courses SET deleted_at = NULL WHERE deleted_at IS NULL AND status != 6;
    -- status=6 (ARCHIVED) 时 deleted_at 应有值
    UPDATE courses SET deleted_at = updated_at
    WHERE status = 6 AND deleted_at IS NULL;
END $$;

-- 3. 增强索引：高频查询路径
CREATE INDEX IF NOT EXISTS idx_enrollments_user_status
    ON enrollments(user_id, enrollment_status)
    WHERE deleted_at IS NULL;

-- 4. 通知按时间倒序索引（避免 filesort）
CREATE INDEX IF NOT EXISTS idx_notifications_user_created
    ON notifications(user_id, created_at DESC);

-- 5. 评价按课程+时间查询索引
CREATE INDEX IF NOT EXISTS idx_course_reviews_course_created
    ON course_reviews(course_id, created_at DESC)
    WHERE parent_id IS NULL;
