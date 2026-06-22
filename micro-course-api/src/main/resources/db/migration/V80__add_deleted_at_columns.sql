-- V80: 为 @TableLogic 新增的 11 张表添加 deleted_at 列
-- 对应 Entity: Banner/Classes/CourseBundle/CourseBundleItem/CourseCategory
--            CourseTagRelation/Department/Major/QuestionTagRelation/Tag/TeachingClass

ALTER TABLE banners ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
ALTER TABLE classes ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
ALTER TABLE course_categories ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
ALTER TABLE course_tag_relations ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
ALTER TABLE departments ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
ALTER TABLE majors ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
ALTER TABLE tags ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
ALTER TABLE question_tag_relations ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
ALTER TABLE teaching_classes ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
ALTER TABLE course_bundles ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
ALTER TABLE course_bundle_items ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
