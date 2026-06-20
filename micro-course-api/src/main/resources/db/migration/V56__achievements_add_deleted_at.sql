-- V56: achievements 表添加 deleted_at 列
-- 修复 MyBatis-Plus @TableLogic 生成 WHERE deleted_at IS NULL 报 "column does not exist" 错误

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='achievements' AND column_name='deleted_at') THEN
        ALTER TABLE achievements ADD COLUMN deleted_at TIMESTAMP DEFAULT NULL;
    END IF;
END $$;

-- 将原有唯一约束替换为部分唯一索引（兼容软删除）
ALTER TABLE achievements DROP CONSTRAINT IF EXISTS uk_ach_user_badge;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname='idx_ach_user_badge') THEN
        CREATE UNIQUE INDEX idx_ach_user_badge ON achievements(user_id, badge_code) WHERE deleted_at IS NULL;
    END IF;
END $$;
