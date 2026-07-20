-- W35 治理: 启用 pg_stat_statements 扩展 (慢查询真实 SQL 统计)
-- 需要在 postgresql.conf 中设置 shared_preload_libraries = 'pg_stat_statements'
-- docker-compose.yml 中已添加对应 command 参数
-- 注意: CI/测试环境可能未预加载该库, 此时扩展创建静默跳过

CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- 验证扩展是否成功创建（非致命）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_extension WHERE extname = 'pg_stat_statements'
    ) THEN
        RAISE NOTICE 'V203: pg_stat_statements extension not available (shared_preload_libraries missing in this environment). Skipping.';
    ELSE
        RAISE NOTICE 'V203: pg_stat_statements extension enabled successfully.';
    END IF;
END $$;
