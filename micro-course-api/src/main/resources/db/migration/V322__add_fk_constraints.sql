-- 【P1-I 修复 2026-07-30】
-- Batch B: 添加缺失的外键约束

-- =============================================================================
-- B1: certificates FK 约束
--   V19 建表时未定义任何外键，补充 user_id → users(id) 约束。
--   同时添加 enrollment_id 列（对应 enrollments 关系），使证书与选课记录关联。
-- =============================================================================

-- 先添加 enrollment_id 列（V19 建表时遗漏）
ALTER TABLE certificates ADD COLUMN IF NOT EXISTS enrollment_id BIGINT;

ALTER TABLE certificates
    ADD CONSTRAINT fk_certificates_enrollment
    FOREIGN KEY (enrollment_id) REFERENCES enrollments(id);

ALTER TABLE certificates
    ADD CONSTRAINT fk_certificates_user
    FOREIGN KEY (user_id) REFERENCES users(id);

-- =============================================================================
-- B2: discussion_comment_likes FK 约束
--   V46 建表时未定义外键，补充 comment_id → discussion_comments(id) 和
--   user_id → users(id) 约束。
-- =============================================================================

ALTER TABLE discussion_comment_likes
    ADD CONSTRAINT fk_dcl_comment
    FOREIGN KEY (comment_id) REFERENCES discussion_comments(id);

ALTER TABLE discussion_comment_likes
    ADD CONSTRAINT fk_dcl_user
    FOREIGN KEY (user_id) REFERENCES users(id);
