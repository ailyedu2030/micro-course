-- U317__videos_original_name_not_null_rollback.sql
-- 对应迁移: V317__videos_original_name_not_null.sql
-- 回滚 V317 对 videos.original_name 列的 NOT NULL 约束

ALTER TABLE videos ALTER COLUMN original_name DROP NOT NULL;
