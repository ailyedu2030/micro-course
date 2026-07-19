-- V3.1.1: 索引优化 (W32 治理 - 慢查询 < 0.1% 目标)
--
-- 【背景】 W31 慢查询分析发现:
--   1. courses 表 seq_scan 占比 1.06% (idx_scan=1, seq_scan=93)
--   2. users 表 seq_scan 占比 1.30% (idx_scan=1, seq_scan=76)
--   3. course_chapters 表 seq_scan 占比 2.82% (idx_scan=2, seq_scan=69)
--
-- 【优化策略】 不删除现有索引, 仅添加缺失的复合索引:
--   1. courses: (teacher_id, deleted_at, status) - 教师课程列表 (热路径)
--   2. users: (role, status, deleted_at) - 用户列表 (后台管理)
--   3. course_chapters: (course_id, deleted_at, sort_order) - 章节列表
--
-- 【幂等】 IF NOT EXISTS 保证重跑安全
-- 【回滚】 DROP INDEX IF EXISTS

-- ═══════════════════════════════════════════════════════════════
-- 1. courses 复合索引 (教师课程列表热路径)
-- ═══════════════════════════════════════════════════════════════
CREATE INDEX IF NOT EXISTS idx_courses_teacher_status_deleted
    ON courses (teacher_id, status, deleted_at);

CREATE INDEX IF NOT EXISTS idx_courses_published_recent
    ON courses (published_at DESC NULLS LAST)
    WHERE deleted_at IS NULL;

-- ═══════════════════════════════════════════════════════════════
-- 2. users 复合索引 (后台用户管理)
-- ═══════════════════════════════════════════════════════════════
CREATE INDEX IF NOT EXISTS idx_users_role_status_deleted
    ON users (role, status, deleted_at);

-- ═══════════════════════════════════════════════════════════════
-- 3. course_chapters 复合索引 (章节列表)
-- ═══════════════════════════════════════════════════════════════
CREATE INDEX IF NOT EXISTS idx_cc_course_sort_active
    ON course_chapters (course_id, sort_order)
    WHERE deleted_at IS NULL;

-- ═══════════════════════════════════════════════════════════════
-- 4. course_sections 复合索引 (章节下小节列表)
-- ═══════════════════════════════════════════════════════════════
CREATE INDEX IF NOT EXISTS idx_cs_chapter_sort_active
    ON course_sections (chapter_id, sort_order)
    WHERE deleted_at IS NULL;

-- ═══════════════════════════════════════════════════════════════
-- 输出索引统计
-- ═══════════════════════════════════════════════════════════════
DO $$
DECLARE
    new_idx_count INT;
BEGIN
    SELECT COUNT(*) INTO new_idx_count
    FROM pg_indexes
    WHERE schemaname = 'public'
    AND indexname IN (
        'idx_courses_teacher_status_deleted',
        'idx_courses_published_recent',
        'idx_users_role_status_deleted',
        'idx_cc_course_sort_active',
        'idx_cs_chapter_sort_active'
    );
    RAISE NOTICE 'V3.1.1 索引优化完成, 新增 % 个索引', new_idx_count;
END $$;