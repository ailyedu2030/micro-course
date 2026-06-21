-- V60: 性能优化索引

-- P0: enrollments 状态筛选频繁
CREATE INDEX IF NOT EXISTS idx_enrollments_status ON enrollments(enrollment_status);

-- P1: 教师课程查询+软删除
CREATE INDEX IF NOT EXISTS idx_courses_teacher_deleted ON courses(teacher_id, deleted_at);

-- P1: 课程状态+软删除部分索引
CREATE INDEX IF NOT EXISTS idx_courses_status_deleted ON courses(status, deleted_at) WHERE deleted_at IS NULL;

-- P2: 选课排序
CREATE INDEX IF NOT EXISTS idx_enrollments_course_enrolled ON enrollments(course_id, enrolled_at DESC);

-- P2: 讨论区章节+状态
CREATE INDEX IF NOT EXISTS idx_dp_chapter_status ON discussion_posts(chapter_id, status);
