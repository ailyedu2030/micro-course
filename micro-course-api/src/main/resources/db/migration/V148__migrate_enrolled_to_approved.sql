-- P1-10 修复：将 enrollments 表中存量的 'ENROLLED' 值迁移为契约值 'APPROVED'
-- 语义等价（均表示"已通过/在读"），迁移后新代码写入 APPROVED 而非 ENROLLED
UPDATE enrollments SET enrollment_status = 'APPROVED' WHERE enrollment_status = 'ENROLLED';
