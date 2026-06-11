-- =============================================================================
-- V15__add_missing_indexes.sql
-- -----------------------------------------------------------------------------
-- Phase 11: 补全缺失的高频查询索引
-- 依据：docs/数据字典.md v0.5 + 查询性能分析
-- =============================================================================

-- learning_progress 高频组合查询：用户+课程
CREATE INDEX IF NOT EXISTS idx_lp_user_course ON learning_progress(user_id, course_id);

-- check_ins 高频组合查询：用户+日期（签到查询、连续签到计算）
CREATE INDEX IF NOT EXISTS idx_ci_user_date ON check_ins(user_id, checkin_date);

-- discussion_posts 高频查询：按章节筛选帖子
CREATE INDEX IF NOT EXISTS idx_dp_chapter ON discussion_posts(chapter_id);

-- exercise_records 高频查询：用户答题记录
CREATE INDEX IF NOT EXISTS idx_er_user ON exercise_records(user_id);

-- course_favorites 高频查询：课程收藏数统计
CREATE INDEX IF NOT EXISTS idx_cf_course ON course_favorites(course_id);

-- =============================================================================
-- End of V15__add_missing_indexes.sql
-- =============================================================================