-- P1-C-2: CourseBundle 乐观锁 @Version
-- P1-I-4: CourseBundleItem 补充 updatedAt
-- 已有数据 version 从 0 开始（首次变更走 CAS → version 0→1）

ALTER TABLE course_bundles ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE course_bundle_items ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;
UPDATE course_bundle_items SET updated_at = created_at WHERE updated_at IS NULL;
