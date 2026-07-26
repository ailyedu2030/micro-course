-- V178_1: V178 的 redo 补偿迁移(重命名自 V178b 以兼容 Flyway)
-- 原 V178b 用 "b" 后缀导致 Flyway 不识别
-- 现在用 V178_1 形式,让 Flyway 自动识别并应用
-- 内容使用 IF NOT EXISTS 保证幂等
--
-- 触发场景: 如果 V178(rollback)被意外执行,本迁移会重新添加 content_type 列
--             确保 V193+ 的迁移可正常运行

ALTER TABLE slide_pages
    ADD COLUMN IF NOT EXISTS content_type VARCHAR(20) NOT NULL DEFAULT 'PPT_RENDERED';

ALTER TABLE slide_pages
    ADD COLUMN IF NOT EXISTS html_content TEXT;

-- 约束单独处理(避免 ADD CONSTRAINT 不支持 IF NOT EXISTS)
DO $do$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_slide_pages_content_type') THEN
    ALTER TABLE slide_pages ADD CONSTRAINT chk_slide_pages_content_type CHECK (content_type IN ('PPT_RENDERED', 'HTML_DIRECT'));
  END IF;
END $do$;
