-- 将 V18 badges 表数据迁移到 achievements 表
INSERT INTO achievements (user_id, badge_code, badge_name, earned_at)
SELECT b.user_id, b.badge_type, b.badge_name, b.earned_at
FROM badges b
WHERE b.deleted_at IS NULL
ON CONFLICT (user_id, badge_code) DO NOTHING;
