-- P1-16 修复：将 enrollments 的 CASCADE 改为 RESTRICT，防止误删用户/课程时级联清空 enrollments
-- 使用 DO 块安全处理（约束名可能因环境不同）
DO $$
BEGIN
    -- enrollments.course_id → courses.id
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints
               WHERE constraint_name = 'enrollments_course_id_fkey'
                 AND table_name = 'enrollments') THEN
        ALTER TABLE enrollments DROP CONSTRAINT enrollments_course_id_fkey;
    END IF;
    ALTER TABLE enrollments ADD CONSTRAINT enrollments_course_id_fkey
        FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE RESTRICT;

    -- enrollments.user_id → users.id
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints
               WHERE constraint_name = 'enrollments_user_id_fkey'
                 AND table_name = 'enrollments') THEN
        ALTER TABLE enrollments DROP CONSTRAINT enrollments_user_id_fkey;
    END IF;
    ALTER TABLE enrollments ADD CONSTRAINT enrollments_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT;
END $$;
