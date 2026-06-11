-- Phase 12: 业务幂等唯一索引
-- 注：V9 已有 UNIQUE (user_id, checkin_date) 约束，无需重复创建
CREATE UNIQUE INDEX IF NOT EXISTS uk_enrollments_user_course ON enrollments(user_id, course_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_course_reviews_user_course ON course_reviews(user_id, course_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_course_favorites_user_course ON course_favorites(user_id, course_id) WHERE deleted_at IS NULL;