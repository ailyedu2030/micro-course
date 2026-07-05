-- =============================================================================
-- V160__add_discussion_reject_reason.sql
-- -----------------------------------------------------------------------------
-- P1C-060: 讨论驳回需填写驳回原因。新增 reject_reason 列保存驳回理由。
--
-- 使用 ALTER TABLE ADD COLUMN IF NOT EXISTS（幂等模式）：
--   在已有该列的环境中为 no-op，无副作用。
--   新环境自动创建该列，默认空字符串。
-- =============================================================================

ALTER TABLE discussion_posts
    ADD COLUMN IF NOT EXISTS reject_reason VARCHAR(500) NOT NULL DEFAULT '';

COMMENT ON COLUMN discussion_posts.reject_reason IS '驳回原因（P1C-060：讨论审核驳回时必填）';
