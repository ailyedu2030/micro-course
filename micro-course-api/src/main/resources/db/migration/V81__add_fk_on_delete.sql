-- V81: 补全缺失的 FK ON DELETE 约束 + 补充高频索引
-- P1 数据完整性修复
-- 日期: 2026-06-23

-- =============================================================================
-- Part 1: FK ON DELETE 约束补齐（DO $$ 块保证幂等）
-- =============================================================================

-- 1.1 orders.user_id → users(id) ON DELETE RESTRICT
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'orders_user_id_fkey' AND table_name = 'orders') THEN
        ALTER TABLE orders DROP CONSTRAINT orders_user_id_fkey;
    END IF;
    ALTER TABLE orders ADD CONSTRAINT orders_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT;
END $$;

-- 1.2 orders.course_id → courses(id) ON DELETE RESTRICT
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'orders_course_id_fkey' AND table_name = 'orders') THEN
        ALTER TABLE orders DROP CONSTRAINT orders_course_id_fkey;
    END IF;
    ALTER TABLE orders ADD CONSTRAINT orders_course_id_fkey FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE RESTRICT;
END $$;

-- 1.3 orders.bundle_id → course_bundles(id) ON DELETE SET NULL
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'orders_bundle_id_fkey' AND table_name = 'orders') THEN
        ALTER TABLE orders DROP CONSTRAINT orders_bundle_id_fkey;
    END IF;
    ALTER TABLE orders ADD CONSTRAINT orders_bundle_id_fkey FOREIGN KEY (bundle_id) REFERENCES course_bundles(id) ON DELETE SET NULL;
END $$;

-- 1.4 course_bundles.creator_id → users(id) ON DELETE SET NULL
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'course_bundles_creator_id_fkey' AND table_name = 'course_bundles') THEN
        ALTER TABLE course_bundles DROP CONSTRAINT course_bundles_creator_id_fkey;
    END IF;
    ALTER TABLE course_bundles ADD CONSTRAINT course_bundles_creator_id_fkey FOREIGN KEY (creator_id) REFERENCES users(id) ON DELETE SET NULL;
END $$;

-- 1.5 course_bundle_items.bundle_id → course_bundles(id) ON DELETE CASCADE
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'course_bundle_items_bundle_id_fkey' AND table_name = 'course_bundle_items') THEN
        ALTER TABLE course_bundle_items DROP CONSTRAINT course_bundle_items_bundle_id_fkey;
    END IF;
    ALTER TABLE course_bundle_items ADD CONSTRAINT course_bundle_items_bundle_id_fkey FOREIGN KEY (bundle_id) REFERENCES course_bundles(id) ON DELETE CASCADE;
END $$;

-- 1.6 course_bundle_items.course_id → courses(id) ON DELETE CASCADE
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'course_bundle_items_course_id_fkey' AND table_name = 'course_bundle_items') THEN
        ALTER TABLE course_bundle_items DROP CONSTRAINT course_bundle_items_course_id_fkey;
    END IF;
    ALTER TABLE course_bundle_items ADD CONSTRAINT course_bundle_items_course_id_fkey FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE;
END $$;

-- 1.7 lessons.chapter_id → course_chapters(id) ON DELETE CASCADE
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'lessons_chapter_id_fkey' AND table_name = 'lessons') THEN
        ALTER TABLE lessons DROP CONSTRAINT lessons_chapter_id_fkey;
    END IF;
    ALTER TABLE lessons ADD CONSTRAINT lessons_chapter_id_fkey FOREIGN KEY (chapter_id) REFERENCES course_chapters(id) ON DELETE CASCADE;
END $$;

-- 1.8 lessons.course_id → courses(id) ON DELETE CASCADE
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'lessons_course_id_fkey' AND table_name = 'lessons') THEN
        ALTER TABLE lessons DROP CONSTRAINT lessons_course_id_fkey;
    END IF;
    ALTER TABLE lessons ADD CONSTRAINT lessons_course_id_fkey FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE;
END $$;

-- 1.9 course_slides.lesson_id → lessons(id) ON DELETE SET NULL
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'course_slides_lesson_id_fkey' AND table_name = 'course_slides') THEN
        ALTER TABLE course_slides DROP CONSTRAINT course_slides_lesson_id_fkey;
    END IF;
    ALTER TABLE course_slides ADD CONSTRAINT course_slides_lesson_id_fkey FOREIGN KEY (lesson_id) REFERENCES lessons(id) ON DELETE SET NULL;
END $$;

-- =============================================================================
-- Part 2: 补充高频查询索引
-- =============================================================================

CREATE INDEX IF NOT EXISTS idx_users_student_no ON users(student_no);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
