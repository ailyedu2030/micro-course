-- =============================================================================
-- V34__grade_components.sql
-- -----------------------------------------------------------------------------
-- 成绩组成表
-- 范围：grade_components
-- 依据：docs/数据字典.md v0.5 §2.11
-- 日期：2026-06-12
-- =============================================================================

-- -----------------------------------------------------------------------------
-- grade_components（成绩组成表）
-- 支持平时/期中/期末等子成绩拆分，每门课程的多环节过程性评价。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS grade_components (
    id              BIGSERIAL       PRIMARY KEY,
    enrollment_id   BIGINT          NOT NULL REFERENCES enrollments(id) ON DELETE CASCADE,
    component_type  VARCHAR(20)     NOT NULL,
    component_name  VARCHAR(50),
    weight          INTEGER         NOT NULL,
    score           DECIMAL(6,2),
    max_score       DECIMAL(6,2),
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_gc_enrollment ON grade_components(enrollment_id);

COMMENT ON TABLE  grade_components IS '成绩组成表';
COMMENT ON COLUMN grade_components.component_type IS 'HOMEWORK / MIDTERM / FINAL / QUIZ / PARTICIPATION';
COMMENT ON COLUMN grade_components.weight IS '权重百分比（0-100）';