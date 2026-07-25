-- =============================================================================
-- seed_student_audit_fixtures.sql
-- 用途：为学生页面审计脚本（audit-student-pages.mjs）提供覆盖全部动态路由
-- 所需的本地测试数据。
--
-- 适用数据库：micro_course_dev（localhost 5432 开发 DB）
--
-- 设计原则：
-- 1. 幂等性：所有 INSERT 使用 ON CONFLICT (id) DO NOTHING，重跑不重复
-- 2. 隔离性：所有 ID 使用 20001+ 段，不冲突现有数据（1-19999）
-- 3. 归属：数据归属 student（id=7），audit 脚本用 student/student123 登录
-- 4. 清理函数：seed_cleanup_student_fixtures() 可一键删除全部 fixture
-- 5. 学生已选课后即可访问课程/章节/视频/练习/讨论等
--
-- 依赖：
--   - seed_users 必须先执行（确保 student id=7 存在）
--   - departments id=1 存在
-- =============================================================================

-- ============================================================
-- 0. 清理函数
-- ============================================================
CREATE OR REPLACE FUNCTION seed_cleanup_student_fixtures() RETURNS void AS $$
BEGIN
  DELETE FROM exercise_records            WHERE exercise_id >= 20001;
  DELETE FROM exercise_questions          WHERE exercise_id >= 20001;
  DELETE FROM exercises                   WHERE id >= 20001;
  DELETE FROM questions                   WHERE id >= 20001;
  DELETE FROM discussion_comment_likes    WHERE comment_id IN (SELECT id FROM discussion_comments WHERE post_id >= 20001);
  DELETE FROM discussion_comments         WHERE post_id >= 20001;
  DELETE FROM discussion_posts            WHERE id >= 20001;
  DELETE FROM learning_progress           WHERE course_id >= 20001;
  DELETE FROM check_ins                   WHERE id >= 20001;
  DELETE FROM enrollment_histories        WHERE enrollment_id >= 20001;
  DELETE FROM enrollments                 WHERE id >= 20001;
  DELETE FROM videos                      WHERE course_id >= 20001;
  DELETE FROM course_chapters              WHERE course_id >= 20001;
  DELETE FROM courses                     WHERE id >= 20001;
  DELETE FROM course_categories           WHERE id >= 20001;
  RAISE NOTICE 'seed_cleanup_student_fixtures: 已清理全部 20001+ 段 fixture 数据';
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- 1. course_categories — 课程分类（课程 FK 依赖）
-- ============================================================
INSERT INTO course_categories (id, name, level, sort_order, created_at, updated_at)
VALUES (20001, '审计-学生测试分类', 1, 1, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- 2. courses — 课程（teacher_id=2=teacher1, 供审计脚本使用）
-- ============================================================
INSERT INTO courses (id, title, subtitle, summary, category_id, teacher_id,
                     offer_department_id, status, difficulty, course_type,
                     is_free, list_price, free_access_scope, discount_scope,
                     discount_percent, pricing_status, student_count,
                     cover_url, hide_in_list, created_at, updated_at)
SELECT 20001, '审计-学生测试课程', '学生端审计用测试课程', '用于学生页面批量审计的测试课程',
       20001, 2, 1, 4, 'BEGINNER', 'VIDEO',
       TRUE, 0, NULL, NULL,
       NULL, 'FIXED', 0,
       NULL, FALSE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM courses WHERE id = 20001);

-- ============================================================
-- 3. chapters — 章节
-- ============================================================
INSERT INTO course_chapters (id, course_id, title, description, sort_order,
                              section_type, duration, created_at, updated_at)
SELECT 20001, 20001, '审计-第一章', '学生端审计用第一章', 1,
       'VIDEO', 600, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM course_chapters WHERE id = 20001);

INSERT INTO course_chapters (id, course_id, title, description, sort_order,
                              section_type, duration, created_at, updated_at)
SELECT 20002, 20001, '审计-第二章(练习)', '学生端审计用练习章节', 2,
       'EXERCISE', 0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM course_chapters WHERE id = 20002);

-- ============================================================
-- 4. enrollment — 学生选课（student id=7 选课 20001）
-- ============================================================
INSERT INTO enrollments (id, user_id, course_id, enrollment_status, source_channel,
                          progress, completed, created_at, updated_at)
SELECT 20001, 7, 20001, 'ENROLLED', 'SEARCH',
        0, FALSE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM enrollments WHERE id = 20001);

-- ============================================================
-- 5. questions — 题目
-- ============================================================
INSERT INTO questions (id, course_id, question_type, content, options, answer,
                       difficulty, explanation, created_at, updated_at)
SELECT 20001, 20001, 'SINGLE', '审计测试题目：1+1=？',
       '[{"label":"A","value":"A","text":"1"},{"label":"B","value":"B","text":"2"},{"label":"C","value":"C","text":"3"}]'::jsonb,
       'B', 1, '审计测试解析', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM questions WHERE id = 20001);

INSERT INTO questions (id, course_id, question_type, content, options, answer,
                       difficulty, explanation, created_at, updated_at)
SELECT 20002, 20001, 'MULTIPLE', '审计多选题：哪些是浏览器？',
       '[{"label":"A","value":"A","text":"Chrome"},{"label":"B","value":"B","text":"Firefox"},{"label":"C","value":"C","text":"微信"}]'::jsonb,
       '["A","B"]', 2, 'Chrome 和 Firefox 是浏览器', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM questions WHERE id = 20002);

-- ============================================================
-- 6. exercises — 练习（关联章节 20002）
-- ============================================================
INSERT INTO exercises (id, course_id, title, description, chapter_id,
                        question_count, pass_score, max_attempts, time_limit,
                        shuffle_questions, created_at, updated_at)
SELECT 20001, 20001, '审计-第一章练习', '审计测试练习',
        20002, 2, 60, 3, 30,
        FALSE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM exercises WHERE id = 20001);

INSERT INTO exercise_questions (exercise_id, question_id, sort_order)
SELECT 20001, 20001, 1
WHERE NOT EXISTS (SELECT 1 FROM exercise_questions WHERE exercise_id = 20001 AND question_id = 20001);

INSERT INTO exercise_questions (exercise_id, question_id, sort_order)
SELECT 20001, 20002, 2
WHERE NOT EXISTS (SELECT 1 FROM exercise_questions WHERE exercise_id = 20001 AND question_id = 20002);

-- ============================================================
-- 7. discussion_posts — 讨论帖
-- ============================================================
INSERT INTO discussion_posts (id, course_id, chapter_id, user_id, title, content,
                               is_anonymous, status, comment_count, created_at, updated_at)
SELECT 20001, 20001, 20001, 7, '审计-测试帖子', '这是一条学生端审计测试帖子内容。',
       FALSE, 1, 0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM discussion_posts WHERE id = 20001);

-- ============================================================
-- 8. discussion_comments — 评论
-- ============================================================
INSERT INTO discussion_comments (id, post_id, parent_id, user_id, content,
                                  is_anonymous, status, created_at, updated_at)
SELECT 20001, 20001, NULL, 2, '教师回复：这是一条测试回复。',
       FALSE, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM discussion_comments WHERE id = 20001);

-- ============================================================
-- 9. learning_progress — 学习进度
-- ============================================================
INSERT INTO learning_progress (id, user_id, course_id, chapter_id,
                                video_progress, video_position, completed,
                                last_watch_at, created_at, updated_at)
SELECT 20001, 7, 20001, 20001,
       50, 300, FALSE, NOW(), NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM learning_progress WHERE id = 20001);

-- ============================================================
-- 10. check_ins — 打卡记录
-- ============================================================
INSERT INTO check_ins (id, user_id, checkin_date, duration, created_at, updated_at)
SELECT 20001, 7, CURRENT_DATE, 300, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM check_ins WHERE id = 20001);

-- ============================================================
-- 11. notifications — 通知
-- ============================================================
INSERT INTO notifications (id, user_id, type, title, content, is_read, created_at, updated_at)
SELECT 20001, 7, 'SYSTEM', '审计-系统通知', '欢迎使用学生端审计测试。', FALSE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM notifications WHERE id = 20001);

INSERT INTO notifications (id, user_id, type, title, content, is_read, created_at, updated_at)
SELECT 20002, 7, 'ENROLLMENT', '审计-选课通知', '您已成功报名「审计-学生测试课程」。', FALSE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM notifications WHERE id = 20002);
