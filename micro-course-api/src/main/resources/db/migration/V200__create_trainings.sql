-- V200: course_trainings — 课程级实训任务
CREATE TABLE IF NOT EXISTS course_trainings (
  id BIGSERIAL PRIMARY KEY,
  course_id BIGINT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
  no INT NOT NULL,
  chapter VARCHAR(100),
  title VARCHAR(200) NOT NULL,
  hours INT NOT NULL,
  submission_form VARCHAR(200),
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  UNIQUE(course_id, no)
);

CREATE INDEX IF NOT EXISTS idx_course_trainings_course ON course_trainings(course_id);

COMMENT ON TABLE course_trainings IS '课程级实训任务(跨节)';
COMMENT ON COLUMN course_trainings.no IS '实训号(1-8)';
COMMENT ON COLUMN course_trainings.hours IS '实训学时';
