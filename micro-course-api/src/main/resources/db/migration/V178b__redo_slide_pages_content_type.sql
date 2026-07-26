-- V178b: 重新应用 V177 变更（针对 V178 回滚后的补偿 forward 迁移）
-- V178 是 V177 的回滚（删除 content_type 和 html_content 列及约束），
-- 若意外执行 V178，本迁移将重新添加这些列和约束。
--
-- 参见 V177__slide_pages_content_type.sql（原始 forward 迁移）

ALTER TABLE slide_pages
    ADD COLUMN IF NOT EXISTS content_type VARCHAR(20) NOT NULL DEFAULT 'PPT_RENDERED',
    ADD COLUMN IF NOT EXISTS html_content TEXT;

ALTER TABLE slide_pages
    ADD CONSTRAINT IF NOT EXISTS chk_slide_pages_content_type CHECK (content_type IN ('PPT_RENDERED', 'HTML_DIRECT'));

-- 索引由 V177b 以 CONCURRENTLY 方式单独处理，此处不再重复创建
