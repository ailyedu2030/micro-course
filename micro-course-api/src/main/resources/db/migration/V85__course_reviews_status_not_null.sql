-- V85: 补齐 course_reviews.status NOT NULL 约束
-- V79 修复遗漏 NOT NULL，已迁移数据库通过 V85 补齐
UPDATE course_reviews SET status = 1 WHERE status IS NULL;
ALTER TABLE course_reviews ALTER COLUMN status SET NOT NULL;
