-- 业务逻辑审计 P2-1：14 张表关键状态字段加 CHECK 约束
-- 所有约束使用 DO $$ ... END $$ 块确保幂等性

-- 用户状态 CHECK
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_users_status') THEN
        ALTER TABLE users ADD CONSTRAINT chk_users_status
            CHECK (status IN (0, 1, 2, 3));
    END IF;
END $$;

-- 用户角色 CHECK
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_users_role') THEN
        ALTER TABLE users ADD CONSTRAINT chk_users_role
            CHECK (role IN ('STUDENT', 'TEACHER', 'ADMIN', 'ACADEMIC'));
    END IF;
END $$;

-- 用户性别 CHECK
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_users_gender') THEN
        ALTER TABLE users ADD CONSTRAINT chk_users_gender
            CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE', 'SECRET'));
    END IF;
END $$;

-- 课程状态 CHECK
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_courses_status') THEN
        ALTER TABLE courses ADD CONSTRAINT chk_courses_status
            CHECK (status IN (0, 1, 2, 3, 4, 5, 6));
    END IF;
END $$;

-- 课程难度 CHECK
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_courses_difficulty') THEN
        ALTER TABLE courses ADD CONSTRAINT chk_courses_difficulty
            CHECK (difficulty IN (1, 2, 3));
    END IF;
END $$;

-- 订单状态 CHECK
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_orders_status') THEN
        ALTER TABLE orders ADD CONSTRAINT chk_orders_status
            CHECK (status IN ('PENDING', 'PAID', 'CANCELLED', 'REFUNDED'));
    END IF;
END $$;

-- 选课状态 CHECK
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_enrollments_status') THEN
        ALTER TABLE enrollments ADD CONSTRAINT chk_enrollments_status
            CHECK (enrollment_status IN ('PENDING', 'APPROVED', 'WAITLIST', 'CANCELLED', 'REJECTED', 'COMPLETED', 'DROPPED'));
    END IF;
END $$;

-- 微专业状态 CHECK
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_ms_status') THEN
        ALTER TABLE micro_specialties ADD CONSTRAINT chk_ms_status
            CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'APPROVED', 'REJECTED', 'RECRUITING', 'COMPLETED', 'CANCELLED', 'ARCHIVED'));
    END IF;
END $$;

-- 修读状态 CHECK
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_mse_status') THEN
        ALTER TABLE micro_specialty_enrollments ADD CONSTRAINT chk_mse_status
            CHECK (status IN ('PENDING', 'APPROVED', 'IN_PROGRESS', 'COMPLETED', 'DROPPED', 'FAILED', 'REJECTED', 'CERTIFIED'));
    END IF;
END $$;

-- 教师邀请状态 CHECK
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_mst_invite_status') THEN
        ALTER TABLE micro_specialty_teachers ADD CONSTRAINT chk_mst_invite_status
            CHECK (invite_status IN ('INVITED', 'ACTIVE', 'PENDING_ACADEMIC', 'DECLINED', 'REMOVED'));
    END IF;
END $$;

-- 申报状态 CHECK
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_msp_status') THEN
        ALTER TABLE micro_specialty_proposals ADD CONSTRAINT chk_msp_status
            CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'APPROVED', 'REJECTED', 'WITHDRAWN'));
    END IF;
END $$;

-- 课程类型 CHECK
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_courses_course_type') THEN
        ALTER TABLE courses ADD CONSTRAINT chk_courses_course_type
            CHECK (course_type IN ('VIDEO', 'INTERACTIVE', 'OFFLINE'));
    END IF;
END $$;

-- 教学班状态 CHECK
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_tc_status') THEN
        ALTER TABLE teaching_classes ADD CONSTRAINT chk_tc_status
            CHECK (status IN (0, 1, 2));
    END IF;
END $$;