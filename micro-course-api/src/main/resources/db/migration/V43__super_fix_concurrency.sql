-- V43__super_fix_concurrency.sql
-- 修复 super-fix 审计发现的并发问题:
-- CON-004: exercise_records attemptNo 并发竞态 — 加 UNIQUE 兜底
-- R2-DB-003 修复:使用部分唯一索引 (WHERE deleted_at IS NULL) 而非全表 UNIQUE,
-- 兼容软删除 (V16 增加 deleted_at 列) — 软删除记录与活跃记录可共存同一 (user, exercise, attempt)

-- 1. 清理软删除记录间的重复:对每个 (user_id, exercise_id, attempt_no) 重复组,仅保留 id 最大的一条
DELETE FROM exercise_records a
USING exercise_records b
WHERE a.user_id = b.user_id
  AND a.exercise_id = b.exercise_id
  AND a.attempt_no = b.attempt_no
  AND a.id < b.id;

-- 2. 加 部分唯一索引 — 仅约束未删除记录,允许软删除与活跃记录共存
CREATE UNIQUE INDEX IF NOT EXISTS uk_er_user_exercise_attempt_active
    ON exercise_records (user_id, exercise_id, attempt_no)
    WHERE deleted_at IS NULL;