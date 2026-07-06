-- P0-3 修复: chk_enrollments_status 缺少 REENROLLING 和 SUSPENDED 状态值
-- 退课重新选课流程使用 REENROLLING 标记旧记录（V164 改为物理删除后已不再写入此值），
-- 但存量数据可能已有此状态值，且 SUSPENDED 在状态机中合法使用。
-- 先删除旧约束再重建，补充缺失的状态值。

ALTER TABLE enrollments DROP CONSTRAINT IF EXISTS chk_enrollments_status;
ALTER TABLE enrollments ADD CONSTRAINT chk_enrollments_status
    CHECK (enrollment_status IN ('PENDING','APPROVED','WAITLIST','CANCELLED',
           'REJECTED','COMPLETED','DROPPED','SUSPENDED','REENROLLING'));
