-- W35 治理: 启用 pg_stat_statements 扩展 (慢查询真实 SQL 统计)
-- 需要在 postgresql.conf 中设置 shared_preload_libraries = 'pg_stat_statements'
-- docker-compose.yml 中已添加对应 command 参数

CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- 验证扩展是否成功创建
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_extension WHERE extname = 'pg_stat_statements'
    ) THEN
        RAISE EXCEPTION 'pg_stat_statements extension creation failed';
    END IF;
END $$;
