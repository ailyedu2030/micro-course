-- Hermes 课程同步映射表
CREATE TABLE hermes_course_mapping (
    id              BIGSERIAL PRIMARY KEY,
    hermes_course_id VARCHAR(64) NOT NULL UNIQUE,
    course_id       BIGINT NOT NULL,
    hermes_teacher_id VARCHAR(64),
    last_sync_at    TIMESTAMP,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_hermes_course_id ON hermes_course_mapping(hermes_course_id);
CREATE INDEX idx_hermes_course_course_id ON hermes_course_mapping(course_id);
