INSERT INTO badge_definitions (code, name, description, icon_url, category, criteria, created_at) VALUES
('FIRST_COURSE', '初识课程', '完成第1门课程', '/icons/badges/first-course.svg', 'COURSE', '{"type":"course_count","value":1}', NOW()),
('ALL_COURSES', '学满全部', '完成所有已选课程', '/icons/badges/all-courses.svg', 'COURSE', '{"type":"all_courses_completed","value":true}', NOW()),
('SEVEN_DAY_STREAK', '连续打卡', '连续7天学习打卡', '/icons/badges/seven-day-streak.svg', 'LEARNING', '{"type":"streak_days","value":7}', NOW()),
('THIRTY_DAY_STREAK', '坚持不懈', '连续30天学习打卡', '/icons/badges/thirty-day-streak.svg', 'LEARNING', '{"type":"streak_days","value":30}', NOW()),
('PERFECT_SCORE', '满分达人', '单次练习获得满分', '/icons/badges/perfect-score.svg', 'EXERCISE', '{"type":"perfect_score","value":true}', NOW()),
('QUICK_LEARNER', '学习先锋', '累计学习达到50小时', '/icons/badges/quick-learner.svg', 'LEARNING', '{"type":"total_hours","value":50}', NOW())
ON CONFLICT (code) DO NOTHING;
