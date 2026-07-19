-- V307: 旧 slide_pages 加 is_legacy 标记 + 视图
--
-- 设计动机: 旧数据保留 3 个月 (V300-V306 已分流出新表)
-- Rollback 路径: ALTER TABLE slide_pages DROP COLUMN is_legacy;
--               DROP VIEW IF EXISTS v_slide_pages_legacy;

ALTER TABLE slide_pages ADD COLUMN IF NOT EXISTS is_legacy BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE slide_pages SET is_legacy = TRUE WHERE is_legacy IS NULL;

COMMENT ON COLUMN slide_pages.is_legacy IS 'DEPRECATED 2026-07-19: 旧字段保留 3 个月,新建课件请用 slide_ppt_pages / slide_html_units';

-- 只读视图,兼容旧前端读路径
CREATE OR REPLACE VIEW v_slide_pages_legacy AS
SELECT * FROM slide_pages WHERE is_legacy = TRUE;

COMMENT ON VIEW v_slide_pages_legacy IS '旧 slide_pages 只读视图 (3 个月保留期)';