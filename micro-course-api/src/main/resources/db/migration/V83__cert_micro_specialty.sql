-- V83__cert_micro_specialty.sql
-- Phase 14: certificates 表扩展，支持微专业结业证书

ALTER TABLE certificates
    ADD COLUMN IF NOT EXISTS cert_type VARCHAR(20) NOT NULL DEFAULT 'COURSE',
    ADD COLUMN IF NOT EXISTS micro_specialty_id BIGINT REFERENCES micro_specialties(id),
    ALTER COLUMN course_id DROP NOT NULL;

-- 清除可能因添加约束前产生的脏数据
UPDATE certificates SET cert_type = 'COURSE' WHERE cert_type IS NULL;

-- 互斥约束：课程证书和微专业证书仅能存在其一
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_cert_xor') THEN
        ALTER TABLE certificates ADD CONSTRAINT chk_cert_xor CHECK (
            (cert_type = 'COURSE' AND course_id IS NOT NULL AND micro_specialty_id IS NULL)
         OR (cert_type = 'MICRO_SPECIALTY' AND micro_specialty_id IS NOT NULL AND course_id IS NULL)
        );
    END IF;
END $$;

-- 同一学生同一微专业仅一个结业证书
CREATE UNIQUE INDEX IF NOT EXISTS uk_cert_ms ON certificates(cert_type, micro_specialty_id, user_id)
    WHERE cert_type = 'MICRO_SPECIALTY';
