-- V201: course_final_project — 课程期末项目
CREATE TABLE IF NOT EXISTS course_final_project (
  id BIGSERIAL PRIMARY KEY,
  course_id BIGINT NOT NULL UNIQUE REFERENCES courses(id) ON DELETE CASCADE,
  title VARCHAR(200) NOT NULL,
  phases TEXT NOT NULL DEFAULT '["选题", "中期", "终期"]',  -- JSON 字符串,同 V196/V197 用 TEXT 存
  final_submission_form VARCHAR(200),
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE course_final_project IS '课程期末项目(每课程 1 个)';
COMMENT ON COLUMN course_final_project.phases IS '阶段 JSON 数组';
