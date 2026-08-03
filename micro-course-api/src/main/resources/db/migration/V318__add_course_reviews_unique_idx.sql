-- V318: 补充 course_reviews 唯一索引 + operation_logs FK
-- R2 交叉验证发现：数据字典声明 idx_cr_course_user (UNIQUE) 但从未创建；
-- operation_logs.user_id 缺失 FK→users 约束。
-- 日期: 2026-07-26

-- =============================================================================
-- 1. course_reviews 唯一索引（用户每课程只能有一条顶级评价）
-- =============================================================================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE tablename = 'course_reviews' AND indexname = 'idx_cr_course_user'
    ) THEN
        CREATE UNIQUE INDEX idx_cr_course_user
            ON course_reviews(course_id, user_id)
            WHERE parent_id IS NULL;
    END IF;
END $$;

-- =============================================================================
-- 2. operation_logs.user_id → users(id) ON DELETE SET NULL
--    日志防篡改：用户删除后日志保留但 userId 置空
-- =============================================================================
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_ol_user' AND table_name = 'operation_logs'
    ) THEN
        ALTER TABLE operation_logs DROP CONSTRAINT fk_ol_user;
    END IF;
    -- 加 FK 前清理孤儿 user_id（历史脏数据：引用已删除/不存在的用户）。
    -- 与 FK ON DELETE SET NULL 语义一致：日志保留，userId 置空。
    -- 2026-08-04 生产部署事故修复：生产存在 16 条孤儿记录导致 ADD CONSTRAINT 校验失败。
    UPDATE operation_logs
    SET user_id = NULL
    WHERE user_id IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM users u WHERE u.id = operation_logs.user_id);
    ALTER TABLE operation_logs
        ADD CONSTRAINT fk_ol_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;
END $$;

COMMENT ON INDEX idx_cr_course_user IS '用户每课程唯一评价（仅顶级评价，parent_id IS NULL）';
