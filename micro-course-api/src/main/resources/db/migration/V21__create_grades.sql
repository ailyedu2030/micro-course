-- V21: 创建 grades 表（学期课程成绩表）
-- 用于存储学生每门课程的总评成绩（百分制），由教师录入或成绩组件汇总
-- Author: jackie
-- Date: 2026-06-12

CREATE TABLE grades (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    exercise_id BIGINT,
    score DECIMAL(6, 2),
    total_score DECIMAL(6, 2),
    passed BOOLEAN DEFAULT false,
    attempt_no INTEGER DEFAULT 1,
    duration INTEGER,
    comment TEXT,
    graded_by BIGINT,
    graded_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);

COMMENT ON TABLE grades IS '成绩记录表';
COMMENT ON COLUMN grades.id IS '主键';
COMMENT ON COLUMN grades.course_id IS '课程ID';
COMMENT ON COLUMN grades.user_id IS '学生用户ID';
COMMENT ON COLUMN grades.exercise_id IS '练习ID';
COMMENT ON COLUMN grades.score IS '得分';
COMMENT ON COLUMN grades.total_score IS '总分';
COMMENT ON COLUMN grades.passed IS '是否及格';
COMMENT ON COLUMN grades.attempt_no IS '第几次答题';
COMMENT ON COLUMN grades.duration IS '用时（秒）';
COMMENT ON COLUMN grades.comment IS '教师评语';
COMMENT ON COLUMN grades.graded_by IS '批改人ID';
COMMENT ON COLUMN grades.graded_at IS '批改时间';
COMMENT ON COLUMN grades.created_at IS '创建时间';
COMMENT ON COLUMN grades.updated_at IS '更新时间';
COMMENT ON COLUMN grades.deleted_at IS '软删除时间戳';

-- 索引
CREATE INDEX idx_grades_course ON grades(course_id);
CREATE INDEX idx_grades_user ON grades(user_id);
CREATE INDEX idx_grades_exercise ON grades(exercise_id);
CREATE INDEX idx_grades_deleted ON grades(deleted_at);