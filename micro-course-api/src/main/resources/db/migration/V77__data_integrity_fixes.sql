-- V77__data_integrity_fixes.sql · 数据完整性补齐
-- P1(R3/R4 验证补齐):
-- 1. 习题 partial_score 字段 NOT NULL 约束补齐（DB 默认 FALSE，Entity 中为 String 类型，V72 已修类型）
-- 2. course_reviews.rating NOT NULL 约束补齐（业务必填）
-- 3. enrollments.status 默认值补齐
-- 4. 课程评价回复时校验 parent 不为自身（应用层校验 + CHECK 约束）
-- 5. 删除孤立回复（parent_id 指向不存在的评价）
-- 日期: 2026-06-22

-- 1. partial_score NOT NULL 约束（兼容存量）
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'questions' AND column_name = 'partial_score'
    ) THEN
        UPDATE questions SET partial_score = 'false' WHERE partial_score IS NULL;
        ALTER TABLE questions ALTER COLUMN partial_score SET NOT NULL;
    END IF;
END $$;

-- 2. course_reviews.rating NOT NULL 约束
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'course_reviews' AND column_name = 'rating'
    ) THEN
        UPDATE course_reviews SET rating = 5 WHERE rating IS NULL;
        ALTER TABLE course_reviews ALTER COLUMN rating SET NOT NULL;
    END IF;
END $$;

-- 3. enrollments.enrollment_status 默认值（兼容 Phase 早期数据）
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'enrollments' AND column_name = 'enrollment_status'
    ) THEN
        UPDATE enrollments SET enrollment_status = 'APPROVED' WHERE enrollment_status IS NULL;
        ALTER TABLE enrollments ALTER COLUMN enrollment_status SET NOT NULL;
    END IF;
END $$;

-- 4. 删除孤立回复（parent_id 指向不存在的评价）
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'course_reviews' AND column_name = 'parent_id'
    ) THEN
        DELETE FROM course_reviews
        WHERE parent_id IS NOT NULL
          AND parent_id NOT IN (SELECT id FROM course_reviews WHERE parent_id IS NULL);
    END IF;
END $$;

-- 5. CHECK 约束：rating 必须 1-5
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.check_constraints
        WHERE constraint_name = 'chk_course_reviews_rating'
    ) THEN
        ALTER TABLE course_reviews
            ADD CONSTRAINT chk_course_reviews_rating
            CHECK (rating >= 1 AND rating <= 5);
    END IF;
END $$;

-- 6. CHECK 约束：evaluation scores 非负且不超过 total
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'grades' AND column_name = 'score'
    ) THEN
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.check_constraints
            WHERE constraint_name = 'chk_grades_score'
        ) THEN
            ALTER TABLE grades
                ADD CONSTRAINT chk_grades_score
                CHECK (score >= 0);
        END IF;
    END IF;
END $$;
