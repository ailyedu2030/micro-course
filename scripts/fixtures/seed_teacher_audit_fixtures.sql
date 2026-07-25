-- =============================================================================
-- seed_teacher_audit_fixtures.sql
-- 用途：为教师页面审计脚本（audit-teacher-pages.mjs）提供覆盖全部动态路由
-- 所需的本地测试数据。
--
-- 适用数据库：micro_course_test（localhost 5433 测试 DB）
--
-- 设计原则：
-- 1. 幂等性：所有 INSERT 使用 ON CONFLICT (id) DO NOTHING，重跑不重复
-- 2. 隔离性：所有 ID 使用 10001+ 段，不冲突现有 7 账号（1-7）
-- 3. 归属：数据归属 teacher1（id=2），audit 脚本用 teacher1 登录后即可访问
-- 4. LEAD 权限：为 teacher1 建立 ACTIVE LEAD 的微专业记录
-- 5. 清理函数：seed_cleanup_audit_fixtures() 可一键删除全部 fixture
--
-- 依赖：
--   - seed_users 必须先执行（确保 teacher1 id=2 存在）
--   - departments id=1 存在（seed_users 创建）
-- =============================================================================

-- ============================================================
-- 0. 清理函数（幂等安全，仅删除本脚本创建的 fixture 数据）
-- ============================================================
CREATE OR REPLACE FUNCTION seed_cleanup_audit_fixtures() RETURNS void AS $$
BEGIN
  -- 按 FK 依赖顺序删除（子表先删）
  DELETE FROM micro_specialty_enrollments       WHERE micro_specialty_id >= 10001;
  DELETE FROM micro_specialty_courses           WHERE micro_specialty_id >= 10001;
  DELETE FROM micro_specialty_teachers          WHERE micro_specialty_id >= 10001;
  DELETE FROM micro_specialty_featured_audit    WHERE micro_specialty_id >= 10001;
  DELETE FROM micro_specialties                WHERE id >= 10001;
  DELETE FROM micro_specialty_proposals         WHERE id >= 10001;
  DELETE FROM exercise_questions                WHERE exercise_id >= 10001;
  DELETE FROM exercise_records                  WHERE exercise_id >= 10001;
  DELETE FROM exercises                         WHERE id >= 10001;
  DELETE FROM questions                         WHERE id >= 10001;
  DELETE FROM discussion_comment_likes          WHERE comment_id IN (SELECT id FROM discussion_comments WHERE post_id >= 10001);
  DELETE FROM discussion_comments               WHERE post_id >= 10001;
  DELETE FROM discussion_posts                  WHERE id >= 10001;
  DELETE FROM videos                            WHERE id >= 10001;
  DELETE FROM course_chapters                   WHERE id >= 10001;
  DELETE FROM courses                           WHERE id >= 10001;
  DELETE FROM course_categories                 WHERE id >= 10001;
  RAISE NOTICE 'seed_cleanup_audit_fixtures: 已清理全部 10001+ 段 fixture 数据';
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- 1. course_categories — 课程分类（课程 FK 依赖）
-- ============================================================
INSERT INTO course_categories (id, name, level, sort_order, created_at, updated_at)
VALUES (10001, '审计测试分类', 1, 1, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- 2. courses — 课程（teacher1=2 所拥有的课程）
-- ============================================================
INSERT INTO courses (id, title, subtitle, summary, category_id, teacher_id,
                     offer_department_id, status, difficulty, course_type,
                     is_free, list_price, free_access_scope, discount_scope,
                     discount_percent, pricing_status, student_count,
                     version, created_at, updated_at)
VALUES (10001, '【审计测试】示例课程', '教师页面审计专用课程', '审计覆盖课程详情所需',
        10001, 2, 1, 4, 2, 'VIDEO',
        true, 0, 'none', 'none', 0, 'APPROVED', 0,
        0, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- 3. course_chapters — 章节（courseId=10001）
-- ============================================================
INSERT INTO course_chapters (id, course_id, title, description, sort_order,
                             duration, version, created_at, updated_at)
VALUES (10001, 10001, '第一章：审计测试章节', '审计覆盖课程章节所需', 1,
         0, 0, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- 4. videos — 视频（courseId=10001, chapterId=10001）
-- ============================================================
INSERT INTO videos (id, course_id, chapter_id, title, original_name, sort_order,
                     file_size, mime_type, status, duration, version, created_at, updated_at)
VALUES (10001, 10001, 10001, '审计测试视频', 'audit-test-video.mp4', 1,
        52428800, 'video/mp4', 2, 300, 0, NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET
    duration      = EXCLUDED.duration,
    file_size     = EXCLUDED.file_size,
    status        = EXCLUDED.status,
    updated_at    = NOW();

-- ============================================================
-- 5. questions — 题目（courseId=10001, teacherId=2）
-- ============================================================
INSERT INTO questions (id, course_id, teacher_id, question_type, content, options, answer,
                       explanation, difficulty, status, version, created_at, updated_at)
VALUES (10001, 10001, 2, 'SINGLE', '审计测试题目：1+1=？',
        '[{"code":"A","content":"2","isCorrect":true},{"code":"B","content":"3","isCorrect":false}]',
        'A', '审计专用测试题', 1, 1, 0, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- 6. exercises — 练习（courseId=10001, chapterId=10001）
-- ============================================================
INSERT INTO exercises (id, course_id, chapter_id, title, pass_score, time_limit,
                       total_score, question_count, shuffle_questions,
                       created_at, updated_at, version)
VALUES (10001, 10001, 10001, '审计测试练习', 60, 0, 100, 1, false,
        NOW(), NOW(), 0)
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- 7. exercise_questions — 练习-题目关联
-- ============================================================
INSERT INTO exercise_questions (exercise_id, question_id, score, sort_order)
VALUES (10001, 10001, 100, 1)
ON CONFLICT (exercise_id, question_id) DO NOTHING;

-- ============================================================
-- 8. discussion_posts — 讨论区帖子（courseId=10001, userId=2）
-- ============================================================
INSERT INTO discussion_posts (id, course_id, chapter_id, user_id, title, content,
                               status, created_at, updated_at)
VALUES (10001, 10001, 10001, 2, '审计测试讨论帖', '这是教师页面审计使用的测试讨论帖内容',
        1, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- 9. micro_specialties — 微专业（teacher1 为负责人）
-- ============================================================
INSERT INTO micro_specialties (id, code, title, subtitle, description,
                                offer_department_id, lead_teacher_id,
                                target_audience, training_objective,
                                completion_rule, total_credits, total_hours,
                                required_course_count, elective_course_count,
                                min_credits, max_students, student_count,
                                status, creator_id, version, created_at, updated_at)
VALUES (10001, 'AUDIT-MS-001', '【审计测试】示例微专业', '教师页面审计专用微专业',
        '审计覆盖微专业详情/工作台/课程编排/团队管理所需',
        1, 2, '审计测试', '验证教师审计覆盖',
        'ALL_REQUIRED', 0, 0, 0, 0, 0, 0, 0,
        'RECRUITING', 2, 0, NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET
    status        = EXCLUDED.status,
    lead_teacher_id = EXCLUDED.lead_teacher_id,
    updated_at    = NOW();

-- ============================================================
-- 10. micro_specialty_teachers — 微专业教师团队（teacher1 为 LEAD）
-- ============================================================
INSERT INTO micro_specialty_teachers (id, micro_specialty_id, teacher_id, role,
                                       invite_status, created_at)
VALUES (10001, 10001, 2, 'LEAD', 'ACTIVE', NOW())
ON CONFLICT (id) DO UPDATE SET
    role          = EXCLUDED.role,
    invite_status = EXCLUDED.invite_status;

-- ============================================================
-- 11. micro_specialty_proposals — 微专业申报表/存储申请表（storage-preview 用）
-- ============================================================
INSERT INTO micro_specialty_proposals (id, proposer_id, title, description,
                                        offer_department_id, status,
                                        created_at, updated_at)
VALUES (10001, 2, '【审计测试】微专业申报表', '审计覆盖存储申请表预览所需',
        1, 'DRAFT', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- 12. 验证：输出 fixture ID 汇总
-- ============================================================
DO $$
BEGIN
  RAISE NOTICE '=== Teacher Audit Fixtures IDs ===';
  RAISE NOTICE 'course_category_id:  10001';
  RAISE NOTICE 'course_id:           10001';
  RAISE NOTICE 'chapter_id:          10001';
  RAISE NOTICE 'video_id:            10001';
  RAISE NOTICE 'question_id:         10001';
  RAISE NOTICE 'exercise_id:         10001';
  RAISE NOTICE 'discussion_post_id:  10001';
  RAISE NOTICE 'micro_specialty_id:  10001';
  RAISE NOTICE 'micro_proposal_id:   10001';
  RAISE NOTICE '===============================';
END $$;

-- ============================================================
-- 13. 清理旧缓存（可选：Flyway 迁移后确保空序列不会意外覆盖 fixture ID）
-- ============================================================
SELECT setval('course_categories_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM course_categories), 10001));
SELECT setval('courses_id_seq',          GREATEST((SELECT COALESCE(MAX(id), 0) FROM courses),          10001));
SELECT setval('course_chapters_id_seq',  GREATEST((SELECT COALESCE(MAX(id), 0) FROM course_chapters),  10001));
SELECT setval('videos_id_seq',           GREATEST((SELECT COALESCE(MAX(id), 0) FROM videos),           10001));
SELECT setval('questions_id_seq',        GREATEST((SELECT COALESCE(MAX(id), 0) FROM questions),        10001));
SELECT setval('exercises_id_seq',        GREATEST((SELECT COALESCE(MAX(id), 0) FROM exercises),        10001));
SELECT setval('discussion_posts_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM discussion_posts), 10001));
SELECT setval('micro_specialties_id_seq',GREATEST((SELECT COALESCE(MAX(id), 0) FROM micro_specialties),10001));
SELECT setval('micro_specialty_teachers_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM micro_specialty_teachers), 10001));
SELECT setval('micro_specialty_proposals_id_seq',GREATEST((SELECT COALESCE(MAX(id), 0) FROM micro_specialty_proposals), 10001));
