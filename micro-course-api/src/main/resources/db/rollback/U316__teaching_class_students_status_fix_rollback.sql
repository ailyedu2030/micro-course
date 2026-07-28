-- U316__teaching_class_students_status_fix_rollback.sql
-- 对应迁移: V316__teaching_class_students_status_fix.sql
-- 回滚 V316 对 teaching_class_students 表的 status 列修改

DO $$
BEGIN
    -- 恢复 status 列的旧默认值（V316 修改前）
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'teaching_class_students' AND column_name = 'status') THEN
        ALTER TABLE teaching_class_students ALTER COLUMN status SET DEFAULT 'PENDING';
    END IF;
END $$;
