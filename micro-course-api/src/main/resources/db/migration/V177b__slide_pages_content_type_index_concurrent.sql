-- V177b: 单独建索引（必须不在事务中执行）
-- Flyway 执行事务时才可用此 migration，部署策略：
--   1. 在 application.yml 中配置 spring.flyway.execute-in-transaction=false
--   2. 或将此 SQL 手动在 psql 中执行（推荐 production 做法）
-- V177 已完成了字段 + CHECK 约束，此索引不阻塞功能。
--
-- 手动执行（首次或回滚后）：
--   psql -d micro_course -c "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_slide_pages_content_type ON slide_pages(content_type);"

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_slide_pages_content_type ON slide_pages(content_type);
