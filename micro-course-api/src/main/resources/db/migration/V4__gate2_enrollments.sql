-- V4__gate2_enrollments.sql · Gate 2 选课+收藏表
-- enrollments / course_favorites
-- 依据: docs/数据字典.md v0.5 §2.7-2.8
-- 日期: 2026-06-11

-- 1. enrollments（选课表）
CREATE TABLE enrollments (
    id                BIGSERIAL   PRIMARY KEY,
    course_id         BIGINT      NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    user_id           BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    progress          DOUBLE PRECISION DEFAULT 0,
    completed         BOOLEAN     DEFAULT FALSE,
    final_score       DECIMAL(6,2),
    final_grade       VARCHAR(10),
    enrollment_status VARCHAR(20),
    source_channel    VARCHAR(30),
    enrolled_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at      TIMESTAMP,
    updated_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_enroll_user_course UNIQUE (user_id, course_id)
);
CREATE INDEX idx_enroll_user   ON enrollments(user_id);
CREATE INDEX idx_enroll_course ON enrollments(course_id);

-- 2. course_favorites（课程收藏表）
CREATE TABLE course_favorites (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    course_id   BIGINT    NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cf_user_course UNIQUE (user_id, course_id)
);
CREATE INDEX idx_cf_user ON course_favorites(user_id);
