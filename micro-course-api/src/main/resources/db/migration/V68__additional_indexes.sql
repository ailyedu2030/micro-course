-- V68__additional_indexes.sql · Round 11-2 性能索引补齐
-- 依据: Round 11-2 慢 SQL 深度优化（补齐管理日志 / 视频进度 / 通知按 type 过滤的覆盖索引）
-- 日期: 2026-06-22
-- 说明: 全部 IF NOT EXISTS 幂等；仅新增索引，不改数据、不改结构；
--       所有列名均已对照各表 CREATE TABLE / ALTER 校验存在（V2 / V9 / V45 / V10）。

-- 操作日志目标类型+动作+时间（管理员日志查询；列 target_type/action 见 V2，action 类型见 V39）
CREATE INDEX IF NOT EXISTS idx_ol_target_action
ON operation_logs(target_type, action, created_at);

-- 视频观看进度 教师端时间范围查询（user_id/video_position 见 V9，lesson_id 见 V45）
CREATE INDEX IF NOT EXISTS idx_lp_user_lesson
ON learning_progress(user_id, lesson_id, video_position);

-- 用户通知 按 type 过滤（user_id/type/created_at 见 V10）
CREATE INDEX IF NOT EXISTS idx_notif_type
ON notifications(user_id, type, created_at DESC);
