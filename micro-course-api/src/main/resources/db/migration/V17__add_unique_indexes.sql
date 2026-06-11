-- Phase 12: 业务幂等唯一索引
CREATE UNIQUE INDEX IF NOT EXISTS uk_enrollments_user_course ON enrollments(user_id, course_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_checkins_user_date ON check_ins(user_id, check_in_date) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_course_reviews_user_course ON course_reviews(user_id, course_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_course_favorites_user_course ON course_favorites(user_id, course_id) WHERE deleted_at IS NULL;