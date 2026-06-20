-- V47: certificates — 添加 (user_id, course_id) 唯一索引，防止并发双发
CREATE UNIQUE INDEX IF NOT EXISTS uk_certificates_user_course
    ON certificates(user_id, course_id);
