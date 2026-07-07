-- V161: 修复微专业教师邀请状态 CHECK 约束 (P1-C)
-- V153 CHECK 只包含 'INVITED','ACTIVE','PENDING_ACADEMIC','DECLINED','REMOVED'
-- MicroSpecialtyInviteServiceImpl.java:287 写入 "REJECTED" 触发约束异常
-- 修复: 重建 CHECK 含 REJECTED

ALTER TABLE micro_specialty_teachers DROP CONSTRAINT IF EXISTS chk_ms_teacher_invite_status;
ALTER TABLE micro_specialty_teachers ADD CONSTRAINT chk_ms_teacher_invite_status
    CHECK (invite_status IN ('INVITED','ACTIVE','PENDING_ACADEMIC','DECLINED','REMOVED','REJECTED'));