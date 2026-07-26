-- V178: V177 的回滚迁移(rollback - 手工执行,不应用于 Flyway 自动跑)
-- 警告: 本迁移是 rollback,不应在生产 forward 迁移流程中自动执行
-- 触发场景: 仅当 V177 在生产引入严重问题时, 由 DBA 手工执行回滚
--
-- 自动执行的事故记录 (2026-07-26):
--   1. 本迁移 V178 被 Flyway 自动识别并执行, DROP 了 content_type + html_content 列
--   2. V178b(redo)因为 "b" 后缀不被 Flyway 识别,无法自动 redo
--   3. V193 (引用 content_type) 失败
--   4. 总工程师手工修复数据库 + 重命名 V177b → V177_1, V178b → V178_1
--
-- 修复后: V177_1 和 V178_1 都被 Flyway 识别并 IF NOT EXISTS 幂等执行
-- 防止再发:
--   - 本 V178 文件保留作 rollback 文档,但 Flyway 已执行过(不会重跑)
--   - 如果需要"清空 slide_pages 内容类型"操作,请用 V178_1 之外的新 forward migration
--   - 强烈建议改名为 U178(Undo Migration 约定),让 Flyway 知道这是 undo(本次未改,保留向后兼容)

ALTER TABLE slide_pages DROP CONSTRAINT IF EXISTS chk_slide_pages_content_type;

DROP INDEX IF EXISTS idx_slide_pages_content_type;

ALTER TABLE slide_pages DROP COLUMN IF EXISTS content_type;
ALTER TABLE slide_pages DROP COLUMN IF EXISTS html_content;
