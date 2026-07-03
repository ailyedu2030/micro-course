-- V113__seed_teacher_ratings.sql
-- 种子数据：教师评级 + 等级历史（为已有教师插入样例数据）
-- 所有插入均为幂等

-- 1) teacher_ratings: 为已有 TEACHER 角色用户插入样例评级
INSERT INTO teacher_ratings (teacher_id, rating_score, tier, avg_student_rating, completion_rate, total_students, total_courses, calculated_at, created_at, updated_at)
SELECT u.id,
    CASE u.id % 4
        WHEN 0 THEN 88.10 WHEN 1 THEN 72.50
        WHEN 2 THEN 45.30 WHEN 3 THEN 22.00
    END,
    CASE u.id % 4
        WHEN 0 THEN 'PLATINUM' WHEN 1 THEN 'GOLD'
        WHEN 2 THEN 'BRONZE'   WHEN 3 THEN 'NEW'
    END,
    CASE u.id % 4
        WHEN 0 THEN 4.8 WHEN 1 THEN 4.2
        WHEN 2 THEN 3.8 WHEN 3 THEN 3.2
    END,
    CASE u.id % 4
        WHEN 0 THEN 92.00 WHEN 1 THEN 68.00
        WHEN 2 THEN 42.00 WHEN 3 THEN 18.00
    END,
    CASE u.id % 4
        WHEN 0 THEN 312 WHEN 1 THEN 156
        WHEN 2 THEN 45  WHEN 3 THEN 12
    END,
    CASE u.id % 4
        WHEN 0 THEN 12 WHEN 1 THEN 8
        WHEN 2 THEN 3  WHEN 3 THEN 2
    END,
    CURRENT_TIMESTAMP - INTERVAL '2 hours', NOW(), NOW()
FROM users u
WHERE u.role = 'TEACHER' AND u.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM teacher_ratings t WHERE t.teacher_id = u.id);

-- 2) teacher_tier_log: 为 GOLD 及以上教师插入历史升级记录
INSERT INTO teacher_tier_log (teacher_id, from_tier, to_tier, reason, triggered_by, created_at)
SELECT tr.teacher_id, v.from_tier, v.to_tier, v.reason, v.triggered_by,
       CURRENT_TIMESTAMP + v.days_offset
FROM teacher_ratings tr
CROSS JOIN (VALUES
    ('NEW', 'BRONZE', '首次评级: 38.50', 'CRON', INTERVAL '-14 days'),
    ('BRONZE', 'SILVER', '评分 52.30 → 升级', 'CRON', INTERVAL '-7 days'),
    ('SILVER', 'GOLD', '评分 72.50 → 升级', 'CRON', INTERVAL '-2 hours')
) AS v(from_tier, to_tier, reason, triggered_by, days_offset)
WHERE tr.tier IN ('GOLD', 'PLATINUM')
  AND NOT EXISTS (SELECT 1 FROM teacher_tier_log l WHERE l.teacher_id = tr.teacher_id);

-- 2b) P2-3 修复: 为 PLATINUM 教师补 GOLD→PLATINUM 升级记录
INSERT INTO teacher_tier_log (teacher_id, from_tier, to_tier, reason, triggered_by, created_at)
SELECT tr.teacher_id, 'GOLD', 'PLATINUM', '评分 88.10 → 升级', 'CRON',
       CURRENT_TIMESTAMP - INTERVAL '2 hours'
FROM teacher_ratings tr
WHERE tr.tier = 'PLATINUM'
  AND NOT EXISTS (SELECT 1 FROM teacher_tier_log l
                  WHERE l.teacher_id = tr.teacher_id AND l.to_tier = 'PLATINUM');
