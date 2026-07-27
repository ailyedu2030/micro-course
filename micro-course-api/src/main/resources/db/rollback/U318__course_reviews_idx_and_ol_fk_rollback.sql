-- U318__course_reviews_idx_and_ol_fk_rollback.sql
-- 对应迁移: V318__add_course_reviews_unique_idx.sql

DROP INDEX IF EXISTS idx_cr_course_user;

ALTER TABLE operation_logs DROP CONSTRAINT IF EXISTS fk_ol_user;
