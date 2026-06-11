-- =============================================================================
-- V16__add_deleted_at_columns.sql
-- -----------------------------------------------------------------------------
-- Phase 11: 软删除支持 - 为所有业务表添加 deleted_at 逻辑删除字段
-- 依据：docs/数据字典.md v0.5 + Phase 11 软删除改造
-- =============================================================================

-- 课程表软删除支持
ALTER TABLE courses ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- 视频表软删除支持
ALTER TABLE videos ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- 课程章节表软删除支持
ALTER TABLE course_chapters ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- 练习表软删除支持
ALTER TABLE exercises ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- 答题记录表软删除支持
ALTER TABLE exercise_records ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- 选课记录表软删除支持
ALTER TABLE enrollments ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- 学习进度表软删除支持
ALTER TABLE learning_progress ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- 课程收藏表软删除支持
ALTER TABLE course_favorites ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- 讨论帖子表软删除支持
ALTER TABLE discussion_posts ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- 讨论回复表软删除支持
ALTER TABLE discussion_comments ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- 签到记录表软删除支持（保留历史签到数据）
ALTER TABLE check_ins ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- 课程评价表软删除支持
ALTER TABLE course_reviews ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- =============================================================================
-- End of V16__add_deleted_at_columns.sql
-- =============================================================================