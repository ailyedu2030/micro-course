-- V20: add is_recommended to courses table
ALTER TABLE courses ADD COLUMN is_recommended BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_courses_is_recommended ON courses(is_recommended) WHERE is_recommended = TRUE;