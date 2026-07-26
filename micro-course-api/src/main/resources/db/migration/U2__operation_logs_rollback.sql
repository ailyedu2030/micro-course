-- U2__operation_logs_rollback.sql · 回滚操作日志表
-- 对应迁移: V2__operation_logs.sql

DROP TABLE IF EXISTS operation_logs CASCADE;
