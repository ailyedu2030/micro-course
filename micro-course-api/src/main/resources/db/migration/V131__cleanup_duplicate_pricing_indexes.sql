-- V131__cleanup_duplicate_pricing_indexes.sql
-- 清理 V108 与 V111 之间的架构重复
--
-- 问题:
--   V108 在 free_access_scope/free_dept_ids/discount_scope/discount_percent 添加了约束
--   chk_course_*。V111 对同一组列添加了第二套约束 chk_courses_*。旧约束已死代码。
--   V111 还添加了 idx_courses_teacher_id(teacher_id)，但 V3 已有 idx_courses_teacher(teacher_id)。

-- 删除 V108 的旧约束（V111 的 chk_courses_* 已完全覆盖）
ALTER TABLE courses DROP CONSTRAINT IF EXISTS chk_course_free_scope;
ALTER TABLE courses DROP CONSTRAINT IF EXISTS chk_course_discount_scope;
ALTER TABLE courses DROP CONSTRAINT IF EXISTS chk_course_discount_percent;

-- 删除 V111 的重复单列索引（V3 的 idx_courses_teacher 已覆盖）
DROP INDEX IF EXISTS idx_courses_teacher_id;
