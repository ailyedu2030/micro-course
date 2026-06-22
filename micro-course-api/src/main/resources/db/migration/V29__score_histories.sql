-- V29__score_histories.sql · 成绩变更历史表
-- 依据: docs/数据字典.md v0.5 §5.3

CREATE TABLE IF NOT EXISTS score_histories (
    id           BIGSERIAL   PRIMARY KEY,
    enrollment_id BIGINT    NOT NULL REFERENCES enrollments(id),
    field_name   VARCHAR(30) NOT NULL,
    old_value    VARCHAR(100),
    new_value    VARCHAR(100) NOT NULL,
    change_type  VARCHAR(20) NOT NULL,
    reason       VARCHAR(500),
    operator_id  BIGINT,
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sh_enrollment ON score_histories(enrollment_id);
CREATE INDEX IF NOT EXISTS idx_sh_operator ON score_histories(operator_id);