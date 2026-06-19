-- V43__super_fix_concurrency.sql
-- 修复 super-fix 审计发现的并发问题:
-- CON-004: exercise_records attemptNo 并发竞态 — 加 UNIQUE 兜底
-- DA-4 安全迁移:先清理已存在的重复 (user_id, exercise_id, attempt_no) 数据,
-- 再加 UNIQUE 约束,避免在生产/测试环境 ALTER TABLE 失败导致 Flyway 启动崩溃

-- 1. 清理重复数据:对每个 (user_id, exercise_id, attempt_no) 重复组,仅保留 id 最大的一条
DELETE FROM exercise_records a
USING exercise_records b
WHERE a.user_id = b.user_id
  AND a.exercise_id = b.exercise_id
  AND a.attempt_no = b.attempt_no
  AND a.id < b.id
  AND a.deleted_at IS NULL
  AND b.deleted_at IS NULL;

-- 2. 加 UNIQUE 约束(等价于创建唯一索引)
ALTER TABLE exercise_records
    ADD CONSTRAINT uk_er_user_exercise_attempt
    UNIQUE (user_id, exercise_id, attempt_no);