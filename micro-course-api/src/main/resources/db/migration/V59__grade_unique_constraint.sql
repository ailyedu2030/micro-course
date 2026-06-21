-- 为 grades 表添加唯一约束，防止重复提交堆积
-- 说明（与原始任务 SQL 的偏差，见下）：
--   1) grades 表的学生列实际为 user_id（文档注释“学生用户ID”），不存在 student_id 列，
--      若按字面使用 student_id 会导致 Flyway 迁移在启动时失败（column does not exist）。
--   2) 业务允许同一 (user, course, exercise) 多次作答（attempt_no 递增，见 ExerciseRecordServiceImpl.submitAnswer），
--      因此唯一键必须包含 attempt_no，否则第二次作答的 grades insert 会在运行期抛 DuplicateKeyException。
--   3) 采用 partial unique index (WHERE deleted_at IS NULL)，与既有 V44 设计一致，避免与软删除行冲突。
ALTER TABLE grades DROP CONSTRAINT IF EXISTS uk_grade_user_exercise;
DROP INDEX IF EXISTS uk_grade_user_exercise;
CREATE UNIQUE INDEX IF NOT EXISTS uk_grade_user_exercise
    ON grades (user_id, course_id, exercise_id, attempt_no)
    WHERE deleted_at IS NULL;
