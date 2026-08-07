-- V329: slide_html_units 增加 is_trusted 教师信任标记（Q-4 HTML XSS 加固）
--
-- 背景: 教师上传的 HTML 课件允许 script/style/onclick/iframe（宽松模式，依赖前端 sandbox 隔离）。
--       is_trusted 标记"此内容为可信教师（课程 owner）上传"，用于:
--         - 审计: 每次 is_trusted=true 上传记录 F-2026-08-07 信任教师审计日志
--         - 读时防御: is_trusted=false 的内容走严格 sanitize（移除 inline handlers）+ CSP nonce
--       V329 把存量单元全部置为 TRUE（与旧行为一致，不破坏现有课件功能）。
-- Rollback 路径: ALTER TABLE slide_html_units DROP COLUMN is_trusted;

ALTER TABLE slide_html_units
    ADD COLUMN IF NOT EXISTS is_trusted BOOLEAN NOT NULL DEFAULT FALSE;

-- 存量教师课件（历史上传）默认可信 —— 保持现有功能不回退（增量、对称）
UPDATE slide_html_units SET is_trusted = TRUE WHERE is_trusted = FALSE;

COMMENT ON COLUMN slide_html_units.is_trusted IS '教师信任标记：TRUE=课程 owner 教师上传（宽松 sanitize + 审计）；FALSE=严格 sanitize + CSP nonce 防御，Q-4';
