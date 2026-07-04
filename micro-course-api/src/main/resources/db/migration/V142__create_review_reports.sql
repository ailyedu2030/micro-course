-- V142__create_review_reports.sql · 举报处理表
-- 举报人提交举报 → 管理员审核(驳回/通过并删除内容) → 记录处理日志
-- 依据: docs/数据字典.md v0.5

CREATE TABLE review_reports (
    id                BIGSERIAL   PRIMARY KEY,
    reporter_id       BIGINT      NOT NULL REFERENCES users(id),
    reported_item_type VARCHAR(50) NOT NULL,  -- 'REVIEW' | 'DISCUSSION_POST' | 'DISCUSSION_COMMENT'
    reported_item_id  BIGINT      NOT NULL,   -- 被举报内容ID
    reason            TEXT        NOT NULL,   -- 举报原因
    status            INTEGER     NOT NULL DEFAULT 0,  -- 0=待处理 1=已驳回(内容保留) 2=已处理(内容已删除)
    reviewer_id       BIGINT      REFERENCES users(id),  -- 审核人
    review_notes      TEXT,                             -- 审核备注
    created_at        TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_review_reports_status   ON review_reports(status);
CREATE INDEX idx_review_reports_reporter ON review_reports(reporter_id);
CREATE INDEX idx_review_reports_item     ON review_reports(reported_item_type, reported_item_id);
