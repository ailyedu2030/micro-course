-- V100: 修复 email 部分唯一约束 - 排除空字符串
-- 背景：批量导入时 187 个用户 email='', 而 uk_users_email 索引条件是 email IS NOT NULL
-- 导致空字符串也算唯一值，互相冲突 → 任何 updateById 触发 DataIntegrityViolationException
-- 修复：
--  1. 把所有 email='' 改为 NULL（NULL 不参与 UNIQUE）
--  2. 重建索引，条件改为 email IS NOT NULL AND email <> ''

-- Step 1: 数据迁移
UPDATE users SET email = NULL WHERE email = '';

-- Step 2: 重建索引
DROP INDEX IF EXISTS uk_users_email;
CREATE UNIQUE INDEX uk_users_email ON public.users (email)
    WHERE (email IS NOT NULL AND email <> '' AND deleted_at IS NULL);
