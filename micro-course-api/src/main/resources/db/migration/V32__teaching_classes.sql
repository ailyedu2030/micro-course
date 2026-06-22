-- =============================================================================
-- V32__teaching_classes.sql
-- -----------------------------------------------------------------------------
-- 教学班表 + 关联表
-- 范围：teaching_classes / teaching_class_students / class_schedules
-- 依据：docs/数据字典.md v0.5 §2.9 / §2.12 / §2.13
-- 日期：2026-06-12
-- =============================================================================

-- -----------------------------------------------------------------------------
-- teaching_classes（教学班表）
-- 同一门课程可分多个教学班，各有不同的学生名单、上课时间和地点。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS teaching_classes (
    id              BIGSERIAL       PRIMARY KEY,
    course_id       BIGINT          NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    teacher_id      BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name            VARCHAR(100)    NOT NULL,
    max_students    INTEGER         DEFAULT 0,
    student_count   INTEGER         DEFAULT 0,
    schedule        VARCHAR(200),
    location        VARCHAR(200),
    semester        VARCHAR(20),
    status          SMALLINT        NOT NULL DEFAULT 1,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         INTEGER         NOT NULL DEFAULT 0
);

CREATE INDEX idx_tc_course    ON teaching_classes(course_id);
CREATE INDEX idx_tc_teacher  ON teaching_classes(teacher_id);
CREATE INDEX idx_tc_semester  ON teaching_classes(semester);

COMMENT ON TABLE  teaching_classes IS '教学班表';
COMMENT ON COLUMN teaching_classes.status IS '0=停开/删除, 1=开课中, 2=已结课';

-- -----------------------------------------------------------------------------
-- teaching_class_students（教学班学生名单表）
-- 记录教学班的学生名单及状态。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS teaching_class_students (
    id          BIGSERIAL       PRIMARY KEY,
    class_id    BIGINT          NOT NULL REFERENCES teaching_classes(id) ON DELETE CASCADE,
    user_id     BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    enrolled_at TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status      VARCHAR(20)    NOT NULL DEFAULT 'ENROLLED',
    CONSTRAINT uk_tcs_class_user UNIQUE (class_id, user_id)
);

CREATE INDEX idx_tcs_class  ON teaching_class_students(class_id);
CREATE INDEX idx_tcs_user  ON teaching_class_students(user_id);

COMMENT ON TABLE  teaching_class_students IS '教学班学生名单表';
COMMENT ON COLUMN teaching_class_students.status IS 'ENROLLED / DROPPED / COMPLETED';

-- -----------------------------------------------------------------------------
-- class_schedules（上课时间表）
-- 结构化存储教学班的具体上课时间，支持多时间段（周几+节次+地点）。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS class_schedules (
    id              BIGSERIAL       PRIMARY KEY,
    class_id        BIGINT          NOT NULL REFERENCES teaching_classes(id) ON DELETE CASCADE,
    day_of_week     SMALLINT        NOT NULL,
    start_period    INTEGER,
    end_period      INTEGER,
    start_time      VARCHAR(5),
    end_time        VARCHAR(5),
    location        VARCHAR(200),
    week_pattern    VARCHAR(20),
    custom_weeks    VARCHAR(100)
);

CREATE INDEX idx_cs_class ON class_schedules(class_id);

COMMENT ON TABLE  class_schedules IS '上课时间表';
COMMENT ON COLUMN class_schedules.day_of_week  IS '星期几（1=周一, 7=周日）';
COMMENT ON COLUMN class_schedules.week_pattern IS 'ALL / ODD / EVEN / CUSTOM';