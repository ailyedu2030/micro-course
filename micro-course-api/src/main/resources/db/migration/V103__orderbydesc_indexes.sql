-- V103: RES-005 修复 — 高频 orderByDesc 列添加排序索引
-- 62 处 orderByDesc(created_at/enrolled_at/submitted_at) 无支持索引
-- 这些索引将被优化器自动选用, filesort → index scan

-- enrollments: orderByDesc enrolled_at (用户选课列表、管理端选课查询)
CREATE INDEX IF NOT EXISTS idx_enrollments_deleted_enrolled
    ON enrollments (enrolled_at DESC);

-- discussion_posts: orderByDesc created_at (讨论列表)
CREATE INDEX IF NOT EXISTS idx_discussion_posts_created
    ON discussion_posts (created_at DESC);

-- exercise_records: orderByDesc submitted_at (练习记录列表)
CREATE INDEX IF NOT EXISTS idx_exercise_records_submitted
    ON exercise_records (submitted_at DESC);

-- certificates: orderByDesc issued_at (证书列表)
CREATE INDEX IF NOT EXISTS idx_certificates_issued
    ON certificates (issued_at DESC);

-- grades: orderByDesc graded_at (成绩列表排序) + 复合查询优化
CREATE INDEX IF NOT EXISTS idx_grades_course_user
    ON grades (course_id, user_id);
