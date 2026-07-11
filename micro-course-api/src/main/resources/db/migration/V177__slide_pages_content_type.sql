-- V177: slide_pages 增加 content_type 和 html_content 字段
-- 支持 HTML 互动课件扩展 (feature/html-interactive-extension)
--
-- 注意：PostgreSQL CREATE INDEX 不支持事务，但 Flyway 默认会在事务中执行 migration。
-- 必须在 V177 中先去掉约束的自动事务，否则 CONCURRENTLY 会失败。
-- 解决方案：使用 spring.flyway.placeholders 配置或单独建索引 migration。
-- 本次保留 inline 写法，但实际生产部署需配合 spring.flyway.execute-in-transaction=false
-- 或在 spring.flyway.migrate 前禁用约束。

ALTER TABLE slide_pages
    ADD COLUMN content_type VARCHAR(20) NOT NULL DEFAULT 'PPT_RENDERED',
    ADD COLUMN html_content TEXT;

-- 索引需在 migration 提交后单独建（不能放在事务中）
-- 实际生产部署必须使用 CONCURRENTLY，禁止锁表
-- 文件: V177b__slide_pages_content_type_index_concurrent.sql (单独 migration)

ALTER TABLE slide_pages
    ADD CONSTRAINT chk_slide_pages_content_type CHECK (content_type IN ('PPT_RENDERED', 'HTML_DIRECT'));
