-- V11__course_reviews.sql · 课程评价表
-- 依据: docs/数据字典.md v0.5 §6.9
-- 日期: 2026-06-12

CREATE TABLE course_reviews (
    id           BIGSERIAL   PRIMARY KEY,
    course_id    BIGINT      NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    user_id      BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rating       SMALLINT    NOT NULL CHECK (rating BETWEEN 1 AND 5),
    content      TEXT,
    is_anonymous BOOLEAN     DEFAULT FALSE,
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_course_reviews_course_id ON course_reviews(course_id);
CREATE INDEX idx_course_reviews_user_id   ON course_reviews(user_id);