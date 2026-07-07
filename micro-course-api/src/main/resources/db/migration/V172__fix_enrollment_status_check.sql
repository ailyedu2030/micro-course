-- V160: 修复选课状态 CHECK 约束（P0 遗留）
-- 问题: chk_enrollments_status 缺少 SUSPENDED 和 REENROLLING
-- 后果: 该两个状态写入 enrollments 表时触发 CHECK 约束异常
-- 修复: 删除旧约束重建含 9 个状态值的新约束

ALTER TABLE enrollments DROP CONSTRAINT IF EXISTS chk_enrollments_status;
ALTER TABLE enrollments ADD CONSTRAINT chk_enrollments_status
    CHECK (enrollment_status IN (
        'PENDING', 'APPROVED', 'WAITLIST', 'CANCELLED',
        'COMPLETED', 'DROPPED', 'REJECTED', 'SUSPENDED', 'REENROLLING'
    ));

COMMENT ON CONSTRAINT chk_enrollments_status ON enrollments IS '选课状态：PENDING=待审核 APPROVED=已通过 WAITLIST=候补 CANCELLED=已取消 COMPLETED=已完成 DROPPED=已退课 REJECTED=已拒绝 SUSPENDED=已暂停 REENROLLING=重选';