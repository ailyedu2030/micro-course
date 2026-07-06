-- V165__fix_mst_unique_index.sql
-- 【I-22修复】uk_mst_active 唯一索引含 nullable course_id
--
-- 【根因】PostgreSQL 对唯一索引中的 NULL 值视为互不相等，
-- 当 course_id IS NULL 时索引不能阻止重复行插入（允许多条 course_id=NULL 的记录）。
--
-- 【修复】使用 COALESCE(course_id, 0) 将 NULL 转为 0，
-- 确保 NULL 场景下也能正确唯一约束。
--
-- 【防止再发】创建含 nullable 列的唯一索引时，必须用 COALESCE 或
-- COALESCE 表达式处理 NULL 值，同时 added/migration review 必须
-- 检查索引列的 NULL 处理策略。

DROP INDEX IF EXISTS uk_mst_active;
CREATE UNIQUE INDEX IF NOT EXISTS uk_mst_active
    ON micro_specialty_teachers(micro_specialty_id, teacher_id, COALESCE(course_id, 0))
    WHERE invite_status NOT IN ('DECLINED', 'REMOVED');
