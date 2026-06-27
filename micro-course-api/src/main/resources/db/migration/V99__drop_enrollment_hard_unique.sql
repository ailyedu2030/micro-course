-- V99: 删除 enrollments 表的硬唯一约束（V4 建表时创建的 uk_enroll_user_course）
--
-- 背景：V4 在建表时定义了 UNIQUE(user_id, course_id) 作用于所有行（含软删除行），
-- 与 V17 新增的 WHERE deleted_at IS NULL 部分唯一索引冲突。
-- 当前代码通过先物理删除旧 CANCELLED 记录来绕过此约束，存在数据丢失窗口。
--
-- 此迁移删除 V4 的硬唯一约束，保留 V17 的部分唯一索引作为真正的唯一保障。
-- 之后可移除 EnrollmentServiceImpl.physicalDeleteById + REQUIRES_NEW 的对抗逻辑。
--
-- 参考：docs/数据字典.md §2.7 enrollments

ALTER TABLE enrollments DROP CONSTRAINT IF EXISTS uk_enroll_user_course;
