-- V67__performance_indexes_v2.sql · P0 性能索引（Round 9-2 修复）
-- 依据: Round 9 性能优化（大表查询全表扫描 → 索引覆盖）
-- 日期: 2026-06-22
-- 说明: 全部 IF NOT EXISTS 幂等；仅新增索引，不改数据、不改结构；
--       所有列名均已对照各表 CREATE TABLE 校验存在。

-- learning_progress.last_watch_at（教师端学生学习活动时间范围查询；列见 V9）
CREATE INDEX IF NOT EXISTS idx_lp_last_watch
ON learning_progress(last_watch_at);

-- course_reviews 课程评价按时间倒序（课程详情页评价列表；列见 V11）
CREATE INDEX IF NOT EXISTS idx_cr_course_created
ON course_reviews(course_id, created_at DESC);

-- exercise_records 教师待批改/成绩查询（列见 V7）
CREATE INDEX IF NOT EXISTS idx_er_exercise_score
ON exercise_records(exercise_id, score, submitted_at);

-- notifications 用户通知列表（按未读 + 时间倒序；列见 V10）
CREATE INDEX IF NOT EXISTS idx_notif_user_read_time
ON notifications(user_id, is_read, created_at DESC);

-- enrollment_histories 审计时间查询（列见 V35）
CREATE INDEX IF NOT EXISTS idx_eh_created_at
ON enrollment_histories(created_at);
