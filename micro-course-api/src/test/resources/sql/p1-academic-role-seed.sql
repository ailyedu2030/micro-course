-- =============================================================================
-- p1-academic-role-seed.sql
-- ACADEMIC角色与越权回归测试种子数据
-- 依赖：p0-seed.sql 已执行（提供 admin/p0_teacher/student/courses 1-4）
-- =============================================================================

-- 1) ACADEMIC 教务处用户（口令: student123，与 p0-seed 其他用户复用同一哈希）
--    关键:ON CONFLICT 强制重置 password,防止被先前的脏 hash 覆盖后无法登录
INSERT INTO users (id, username, password, real_name, role, status, cas_bound, department_id, created_at, updated_at)
VALUES (100, 'academic_user',
        '$2b$12$8INfOluI..wPsed6wvZSsOxfoH/dzsxaXvPR5ABQffWVKyjH7gcmK',
        '教务处测试用户', 'ACADEMIC', 1, FALSE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE
SET username   = EXCLUDED.username,
    password   = EXCLUDED.password,
    real_name  = EXCLUDED.real_name,
    role       = EXCLUDED.role,
    status     = EXCLUDED.status,
    cas_bound  = EXCLUDED.cas_bound,
    department_id = EXCLUDED.department_id,
    updated_at = CURRENT_TIMESTAMP;

-- 2) Course 5: teacher_id=6（验证其他教师无权访问此课程的场景，与 teacherToken 同主体）
--    关键:ON CONFLICT 必须强制重置 teacher_id/title,否则之前测试残留脏数据会让角色测试 403
INSERT INTO courses (id, title, category_id, teacher_id, status, is_free, price,
                     course_type, version, is_recommended, published_at, created_at, updated_at)
VALUES (5, 'P1其他教师课程-6', 1, 6, 4, TRUE, NULL, 'VIDEO', 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE
SET title      = EXCLUDED.title,
    teacher_id = EXCLUDED.teacher_id,
    status     = EXCLUDED.status,
    is_free    = EXCLUDED.is_free,
    updated_at = CURRENT_TIMESTAMP;

-- 3) Course 6: teacher_id=22（invite_teacher，用于验证其他教师课程越权）
--    关键:同样强制重置 teacher_id/title
INSERT INTO courses (id, title, category_id, teacher_id, status, is_free, price,
                     course_type, version, is_recommended, published_at, created_at, updated_at)
VALUES (6, 'P1其他教师课程', 1, 22, 4, TRUE, NULL, 'VIDEO', 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE
SET title      = EXCLUDED.title,
    teacher_id = EXCLUDED.teacher_id,
    status     = EXCLUDED.status,
    is_free    = EXCLUDED.is_free,
    updated_at = CURRENT_TIMESTAMP;

-- 4) 学生 7 在 course 1/5/6 的选课记录（course 1 → teacher_id=6, course 5 → teacher_id=6, course 6 → teacher_id=22）
INSERT INTO enrollments (user_id, course_id, enrollment_status, progress, completed, enrolled_at, updated_at, version)
VALUES
    (7, 1, 'APPROVED', 25.0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (7, 5, 'APPROVED', 30.0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (7, 6, 'APPROVED', 50.0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT (user_id, course_id) WHERE deleted_at IS NULL DO NOTHING;

-- 5) Grade 记录（使用高 ID 避免与存量数据冲突）
--    关键:grade 必须落在不与其他测试竞争的 (course_id, user_id) 组合上,
--    否则 teacherGrade 内部 selectOne 抛 TooManyResultsException 导致 500。
--    grade 999001: course 5 (teacher_id=6)   → TEACHER(id=6) 课主可查看, OTHER_TEACHER(id=22) 不可
--    grade 999002: course 6 (teacher_id=22)  → OTHER_TEACHER(id=22) 课主可查看, TEACHER(id=6) 不可
--    (course 1 不使用,避免与 GradeFlowIntegrationTest 的固定 fixture 冲突)
INSERT INTO grades (id, course_id, user_id, score, total_score, graded_by, graded_at, created_at, updated_at, version)
VALUES
    (999001, 5, 7, 85, 100, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (999002, 6, 7, 92, 100, 22, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT (id) DO UPDATE
SET course_id = EXCLUDED.course_id,
    user_id   = EXCLUDED.user_id,
    score     = EXCLUDED.score;

-- 6) 推进序列防碰撞
SELECT setval(pg_get_serial_sequence('users', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 100) FROM users), 100));
SELECT setval(pg_get_serial_sequence('courses', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 10) FROM courses), 10));
