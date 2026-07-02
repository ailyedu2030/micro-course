-- V110__fix_free_dept_ids_type.sql
-- 修复: 将 free_dept_ids 从 JSONB 改为 TEXT
-- 原因: JSONB 与 MyBatis-Plus String 映射在测试 seed 中不兼容
-- V108 的初始版本误用了 JSONB, V110 统一改为 TEXT

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns 
               WHERE table_name='courses' AND column_name='free_dept_ids'
               AND data_type = 'jsonb') THEN
        ALTER TABLE courses ALTER COLUMN free_dept_ids TYPE TEXT USING free_dept_ids::text;
    END IF;
END $$;
