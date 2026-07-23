-- =============================================================================
-- p0-seed.sql  ·  P0 回归测试种子数据 fixture
-- =============================================================================
-- 清理：删除可能来自之前测试运行的微专业报名/邀请/申请数据
DELETE FROM micro_specialty_enrollments;
DELETE FROM micro_specialty_teachers WHERE id > 1;
DELETE FROM micro_specialty_courses WHERE id > 1;
DELETE FROM micro_specialties WHERE id > 1;
-- -----------------------------------------------------------------------------
-- 根因：测试库（micro_course_test）经 Flyway 迁移后仅含 V1 种子的 admin(id=1)，
--       无任何 student 账号 / course / chapter / category。而 4 个 P0 回归测试
--       硬编码了 student/student123、courseId=1..4、chapterId=1/5、course_id=1，
--       导致：
--         · EnrollmentP0ConcurrencyTest   → 登录失败 1001「用户名或密码错误」
--         · LearningProgressP0ConcurrencyTest → 6001「课程不存在」
--         · VideoP0ConcurrencyTest / VideoUploadP0ErrorTest → FK videos_course_id_fkey 违反
--
-- 修复策略（task 约束#5：先修种子数据，不绕过测试断言）：
--   补齐完整依赖链 course_categories → users(teacher/student) → courses → chapters，
--   使硬编码 ID 解析成功。脚本为「附加 + 幂等」（ON CONFLICT DO NOTHING），
--   可随 @Sql(BEFORE_TEST_METHOD) 反复执行而无副作用。
--
-- 业务校验对齐（已逐条核对 src/main 业务代码，无需改业务代码）：
--   · EnrollmentServiceImpl.enroll：课程需 EXISTS + is_free=TRUE（付费课程拦截）+ user EXISTS
--   · LearningProgressServiceImpl.createProgress：courseId EXISTS + chapterId EXISTS
--   · status=4=PUBLISHED（CourseStatus 枚举），仅为语义清晰；服务层只校验存在性
--
-- bcrypt 口令：student123 / p0teacher123（$2b$12$，与 V1 admin 同算法同 cost）
-- =============================================================================

-- 1) 课程分类（courses.category_id 的 NOT NULL FK）
INSERT INTO course_categories (id, name, level, sort_order, created_at, updated_at)
VALUES (1, 'P0测试分类', 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- 2) 教师账号（courses.teacher_id 的 NOT NULL FK；StorageApplication 正向流要求教师绑定学院）
INSERT INTO users (id, username, password, real_name, role, status, cas_bound, department_id, created_at, updated_at)
VALUES (6, 'p0_teacher',
        '$2b$12$8INfOluI..wPsed6wvZSsOxfoH/dzsxaXvPR5ABQffWVKyjH7gcmK',
        'P0测试教师', 'TEACHER', 1, FALSE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE
SET department_id = EXCLUDED.department_id;

-- 3) 学生账号 student/student123（EnrollmentP0ConcurrencyTest 以此登录，且 body userId=7）
INSERT INTO users (id, username, password, real_name, role, status, cas_bound, created_at, updated_at)
VALUES (7, 'student',
        '$2b$12$8INfOluI..wPsed6wvZSsOxfoH/dzsxaXvPR5ABQffWVKyjH7gcmK',
        'P0测试学生', 'STUDENT', 1, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- 4) 课程 1..4（免费 + 已发布；is_free=TRUE 以通过选课付费拦截）
INSERT INTO courses (id, title, category_id, teacher_id, status, is_free, price,
                     course_type, version, is_recommended, published_at, created_at, updated_at)
VALUES
  (1, 'P0测试课程1', 1, 6, 4, TRUE, NULL, 'VIDEO', 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (2, 'P0测试课程2', 1, 6, 4, TRUE, NULL, 'VIDEO', 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (3, 'P0测试课程3', 1, 6, 4, TRUE, NULL, 'VIDEO', 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (4, 'P0测试课程4', 1, 6, 4, TRUE, NULL, 'VIDEO', 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- 5) 章节：id=1 → 课程1（LearningProgress createTestProgress(1,1)）
--          id=5 → 课程2（LearningProgress createTestProgress(2,5)）
INSERT INTO course_chapters (id, course_id, title, sort_order, version, created_at, updated_at)
VALUES
  (1, 1, 'P0测试章节1', 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (5, 2, 'P0测试章节5', 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- 5b) 章节对应的课时（course_sections），替代被删除的 chapter_type/lessons
INSERT INTO course_sections (id, chapter_id, course_id, title, section_type, sort_order, version, created_at, updated_at)
VALUES
  (1, 1, 1, 'P0测试课时1', 'VIDEO', 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (5, 5, 2, 'P0测试课时5', 'VIDEO', 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- 6) 推进序列至显式 ID 之上，避免后续 API 自增插入（createVideo/createCourse 等）主键碰撞
SELECT setval(pg_get_serial_sequence('course_categories', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 1) FROM course_categories), 1));
SELECT setval(pg_get_serial_sequence('users', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 7) FROM users), 7));
SELECT setval(pg_get_serial_sequence('courses', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 4) FROM courses), 4));
SELECT setval(pg_get_serial_sequence('course_chapters', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 5) FROM course_chapters), 5));
SELECT setval(pg_get_serial_sequence('course_sections', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 5) FROM course_sections), 5));

-- =============================================================================
-- 7) 微专业种子数据（供 MicroSpecialtyEnrollmentFlowTest / InviteFlowTest）
-- =============================================================================
-- 7a) 额外测试用户 ID=22（供 InviteFlowTest 邀请使用）
INSERT INTO users (id, username, password, real_name, role, status, cas_bound, created_at, updated_at)
VALUES (22, 'invite_teacher',
        '$2b$12$8INfOluI..wPsed6wvZSsOxfoH/dzsxaXvPR5ABQffWVKyjH7gcmK',
        '被邀请教师', 'TEACHER', 1, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- 7b) 微专业主表（status=RECRUITING，依赖 departments(id=1) + users(id=6)）
INSERT INTO micro_specialties (id, code, title, offer_department_id, lead_teacher_id, status, max_students,
                               total_credits, total_hours, required_course_count, student_count,
                               is_featured, featured_status, is_gold_featured, creator_id,
                               created_at, updated_at, version)
VALUES (1, 'P0_TEST_MS', 'P0测试微专业', 1, 6, 'RECRUITING', 100,
        3, 48, 1, 0,
        FALSE, 'NONE', FALSE, 6,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT (id) DO UPDATE SET max_students = EXCLUDED.max_students, student_count = 0;

-- 7c) 课程编排（依赖 micro_specialties(id=1) + courses(id=1)）
INSERT INTO micro_specialty_courses (id, micro_specialty_id, course_id, sort_order, is_required,
                                     credits, hours, min_score, created_at)
VALUES (1, 1, 1, 1, TRUE, 3, 48, 60, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- 7d) 教师团队（LEAD，依赖 micro_specialties(id=1) + users(id=6)）
INSERT INTO micro_specialty_teachers (id, micro_specialty_id, teacher_id, role, invite_status,
                                      invited_by, invited_at, invite_expires_at, joined_at, created_at)
VALUES (1, 1, 6, 'LEAD', 'ACTIVE',
        1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '7 days', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- 7e) 推进序列
SELECT setval(pg_get_serial_sequence('micro_specialties', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 1) FROM micro_specialties), 1));
SELECT setval(pg_get_serial_sequence('micro_specialty_courses', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 1) FROM micro_specialty_courses), 1));
SELECT setval(pg_get_serial_sequence('micro_specialty_teachers', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 1) FROM micro_specialty_teachers), 1));
