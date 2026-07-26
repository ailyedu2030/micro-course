-- U36__course_review_logs_rollback.sql · 回滚课程审核日志表
-- 对应迁移: V36__course_review_logs.sql

DROP TABLE IF EXISTS course_review_logs CASCADE;
