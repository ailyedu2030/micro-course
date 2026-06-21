-- V70__performance_indexes_v3.sql · Round 11-3 性能索引补齐
-- 依据: 全量交叉验证 R2 缺失索引分析（9 个 P1 推荐索引）
-- 日期: 2026-06-22
-- 说明: 全部 IF NOT EXISTS 幂等；仅新增索引，不改数据、不改结构；
--       全部列名已对照各表 CREATE TABLE / ALTER 校验存在。

-- P1-1: 讨论区帖子 course_id + 创建时间倒序（教师端列表分页）
CREATE INDEX IF NOT EXISTS idx_dp_course_created
    ON discussion_posts(course_id, created_at DESC);

-- P1-2: 视频 course_id + 排序 + 创建时间（视频列表分页）
CREATE INDEX IF NOT EXISTS idx_videos_course_sort
    ON videos(course_id, sort_order, created_at);

-- P1-3: 选课 course_id + 状态（批量迭代 + 状态过滤）
CREATE INDEX IF NOT EXISTS idx_enrollments_course_status
    ON enrollments(course_id, enrollment_status);

-- P1-4: 选课 course_id + 软删除（教师端跨课程查询）
CREATE INDEX IF NOT EXISTS idx_enrollments_course_deleted
    ON enrollments(course_id, deleted_at);

-- P1-5: 学习进度 course_id + 最后观看时间（教师端时间范围查询）
CREATE INDEX IF NOT EXISTS idx_lp_course_last_watch
    ON learning_progress(course_id, last_watch_at);

-- P1-6: 讨论区回复 post_id + 创建时间（评论列表时间排序）
CREATE INDEX IF NOT EXISTS idx_dc_post_created
    ON discussion_comments(post_id, created_at);

-- P1-7: 成绩 course_id + user_id + 创建时间倒序（成绩分页列表）
CREATE INDEX IF NOT EXISTS idx_grades_course_user_created
    ON grades(course_id, user_id, created_at DESC);

-- P1-8: 操作日志 user_id + 创建时间倒序（管理员查用户日志）
CREATE INDEX IF NOT EXISTS idx_ol_user_created
    ON operation_logs(user_id, created_at DESC);

-- P1-9: 答题记录 exercise_id + 提交时间（按练习查提交时间排序）
CREATE INDEX IF NOT EXISTS idx_er_exercise_submitted
    ON exercise_records(exercise_id, submitted_at DESC);
