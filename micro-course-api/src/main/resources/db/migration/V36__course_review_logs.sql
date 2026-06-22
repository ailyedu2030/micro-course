-- =============================================================================
-- V36__course_review_logs.sql
-- -----------------------------------------------------------------------------
-- 课程审核日志表
-- 范围：course_review_logs
-- 依据：docs/数据字典.md v0.5 §2.15
-- 日期：2026-06-12
-- =============================================================================

-- -----------------------------------------------------------------------------
-- course_review_logs（课程审核日志表）
-- 记录课程审核操作的完整审计轨迹。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS course_review_logs (
    id              BIGSERIAL       PRIMARY KEY,
    course_id       BIGINT          NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    reviewer_id     BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    action          VARCHAR(30)     NOT NULL,
    reason          VARCHAR(500),
    previous_status SMALLINT        NOT NULL,
    new_status      SMALLINT        NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_crl_course      ON course_review_logs(course_id);
CREATE INDEX idx_crl_reviewer   ON course_review_logs(reviewer_id);
CREATE INDEX idx_crl_created_at ON course_review_logs(created_at);

COMMENT ON TABLE  course_review_logs IS '课程审核日志表';
COMMENT ON COLUMN course_review_logs.action IS 'SUBMIT / APPROVE / REJECT / PUBLISH / CLOSE / ARCHIVE';
COMMENT ON COLUMN course_review_logs.previous_status IS '审核前的课程状态';
COMMENT ON COLUMN course_review_logs.new_status IS '审核后的课程状态';