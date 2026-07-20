-- V309: slide_ppt_pages 加 version 字段 (乐观锁)
--
-- 【审计修复 BUG #7】 PptCoursewareService.updatePage 无并发控制,
-- 两个教师同时编辑同一 PPT page 会丢失更新 (lost update).
-- 用 MyBatis-Plus @Version 实现乐观锁, 第二次写入会被拒绝.
--
-- Rollback 路径: ALTER TABLE slide_ppt_pages DROP COLUMN version;

ALTER TABLE slide_ppt_pages
    ADD COLUMN version INT NOT NULL DEFAULT 1;

-- 同时为 HTML unit / PPT script / HTML segment script 也加上,保持一致
ALTER TABLE slide_html_units
    ADD COLUMN version INT NOT NULL DEFAULT 1;

ALTER TABLE slide_ppt_page_scripts
    ADD COLUMN version INT NOT NULL DEFAULT 1;

ALTER TABLE slide_html_segment_scripts
    ADD COLUMN version INT NOT NULL DEFAULT 1;

COMMENT ON COLUMN slide_ppt_pages.version IS '乐观锁版本号, MyBatis-Plus @Version';
COMMENT ON COLUMN slide_html_units.version IS '乐观锁版本号, MyBatis-Plus @Version';
COMMENT ON COLUMN slide_ppt_page_scripts.version IS '乐观锁版本号, MyBatis-Plus @Version';
COMMENT ON COLUMN slide_html_segment_scripts.version IS '乐观锁版本号, MyBatis-Plus @Version';