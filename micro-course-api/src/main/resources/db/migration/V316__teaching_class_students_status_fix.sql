-- =============================================================================
-- V316__teaching_class_students_status_fix.sql
-- -----------------------------------------------------------------------------
-- 修复 teaching_class_students.status 漂移 —— 统一到数据字典唯一词汇
-- ENROLLED / DROPPED / COMPLETED。
--
-- 背景：历史代码写入了 APPROVED（借用了 EnrollmentStatus 枚举）、ACTIVE、
--       DISABLED、SUSPENDED、CANCELLED 等非法值，与 V32 建表注释约定的
--       "ENROLLED / DROPPED / COMPLETED" 不一致。
--
-- 迁移映射规则（无冲突原则）：
--   APPROVED, ACTIVE       → ENROLLED（保留在读关系）
--   CANCELLED, DISABLED    → DROPPED（已退出关系）
--   SUSPENDED              → ENROLLED（优先保留成员关系，SUSPENDED 程度轻于删除，
--                              且数据字典无对应词汇，映射为 ENROLLED 保留在读资格）
--   COMPLETED              → COMPLETED（保持）
--   DROPPED                → DROPPED（保持）
--   NULL                   → 'ENROLLED'（DB DEFAULT，理论不应出现）
--
-- 最后添加 CHECK 约束防止再发。
--
-- 依据：docs/数据字典.md §2.12 + V32 建表 COMMENT 注释。
-- =============================================================================

-- Step 1: 幂等迁移 —— 将历史非法值归一为契约三值
UPDATE teaching_class_students
SET status = 'ENROLLED'
WHERE status IN ('APPROVED', 'ACTIVE');

UPDATE teaching_class_students
SET status = 'DROPPED'
WHERE status IN ('CANCELLED', 'DISABLED');

-- SUSPENDED → ENROLLED（优先保留成员关系，非终态轻度值不宜映射为 DROPPED）
-- 注：SUSPENDED 在 enrollment 域是独立状态，但在 teaching_class_students 域
-- 无对应词汇。选择 ENROLLED 以保留教学班成员资格，避免学生被错误移出。
UPDATE teaching_class_students
SET status = 'ENROLLED'
WHERE status = 'SUSPENDED';

-- Step 2: 添加 CHECK 约束（幂等：若已存在则跳过）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_tcs_status'
          AND conrelid = 'teaching_class_students'::regclass
    ) THEN
        ALTER TABLE teaching_class_students
        ADD CONSTRAINT chk_tcs_status
        CHECK (status IN ('ENROLLED', 'DROPPED', 'COMPLETED'));
    END IF;
END $$;

-- Step 3: 更新表注释（幂等）
COMMENT ON COLUMN teaching_class_students.status IS '状态：ENROLLED（在读）/ DROPPED（已退出）/ COMPLETED（已完成）';
