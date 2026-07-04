-- V136__drop_old_enrollment_unique.sql
-- 清理 V4 创建的全表 UNIQUE 约束 uk_enroll_user_course，
-- 已由 V17 的 PARTIAL UNIQUE INDEX（WHERE deleted_at IS NULL）替代。
-- 全表 UNIQUE 与软删除机制冲突：不允许同一 user_id + course_id 存在多条记录。
-- 仅在约束存在时 DROP，确保幂等。

DROP INDEX IF EXISTS uk_enroll_user_course;
