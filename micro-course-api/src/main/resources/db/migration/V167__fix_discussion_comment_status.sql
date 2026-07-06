-- V167__fix_discussion_comment_status.sql
-- 修复存量评论状态：将所有 PENDING(0) 评论标记为 PUBLISHED(1)
-- 【根因】此前删除 setStatus(0) 仅修复了新创建的评论。此前创建的 status=0 评论在数据库中仍不可见。
-- 【修复】UPDATE 将所有 status=0 的评论更新为 status=1
-- 【防止再发】评论创建流程已确保新评论 status 默认 1，无需额外拦截

UPDATE discussion_comments SET status = 1 WHERE status = 0;
