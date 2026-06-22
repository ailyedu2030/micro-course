-- =============================================================================
-- V33__course_prerequisites.sql
-- -----------------------------------------------------------------------------
-- 课程先修关系表
-- 范围：course_prerequisites
-- 依据：docs/数据字典.md v0.5 §2.10
-- 日期：2026-06-12
-- =============================================================================

-- -----------------------------------------------------------------------------
-- course_prerequisites（课程先修关系表）
-- 定义课程之间的先修关系，支持必修/选修先修及最低分要求。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS course_prerequisites (
    id                      BIGSERIAL       PRIMARY KEY,
    course_id               BIGINT          NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    prerequisite_course_id  BIGINT          NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    is_required             BOOLEAN         DEFAULT TRUE,
    min_score               INTEGER,
    created_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cp_course_prerequisite UNIQUE (course_id, prerequisite_course_id)
);

CREATE INDEX idx_cp_course        ON course_prerequisites(course_id);
CREATE INDEX idx_cp_prerequisite ON course_prerequisites(prerequisite_course_id);

COMMENT ON TABLE  course_prerequisites IS '课程先修关系表';
COMMENT ON COLUMN course_prerequisites.is_required IS '是否必修先修';
COMMENT ON COLUMN course_prerequisites.min_score IS '先修课最低分要求';