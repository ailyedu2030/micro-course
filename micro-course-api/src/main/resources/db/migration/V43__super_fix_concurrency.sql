-- V43__super_fix_concurrency.sql
-- 修复 super-fix 审计发现的并发问题:
-- CON-004: exercise_records attemptNo 并发竞态 — 加 UNIQUE 兜底
ALTER TABLE exercise_records
    ADD CONSTRAINT uk_er_user_exercise_attempt
    UNIQUE (user_id, exercise_id, attempt_no);