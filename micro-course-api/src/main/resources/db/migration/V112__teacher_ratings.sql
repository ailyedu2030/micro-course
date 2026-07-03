-- V112__teacher_ratings.sql
-- Phase 2.1: 教师评级表 + 等级升降记录
-- 评级公式: rating_score = avg_student_rating*20*0.4 + completion_rate*0.3 + enrollment_rate*100*0.15 + course_count_factor*0.15
-- 等级: NEW(新) < BRONZE(青铜) < SILVER(白银) < GOLD(黄金) < PLATINUM(铂金)

CREATE TABLE IF NOT EXISTS teacher_ratings (
    id                 BIGSERIAL    PRIMARY KEY,
    teacher_id         BIGINT       NOT NULL REFERENCES users(id),
    rating_score       DECIMAL(5,2) NOT NULL DEFAULT 0,
    tier               VARCHAR(20)  NOT NULL DEFAULT 'NEW',
    avg_student_rating DECIMAL(3,2),
    completion_rate    DECIMAL(5,2),
    total_students     INT          NOT NULL DEFAULT 0,
    total_courses      INT          NOT NULL DEFAULT 0,
    calculated_at      TIMESTAMP,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_teacher_ratings_teacher UNIQUE (teacher_id)
);

COMMENT ON TABLE  teacher_ratings IS '教师评级';
COMMENT ON COLUMN teacher_ratings.rating_score IS '综合评分 0-100';
COMMENT ON COLUMN teacher_ratings.tier IS '等级: NEW/BRONZE/SILVER/GOLD/PLATINUM';
COMMENT ON COLUMN teacher_ratings.avg_student_rating IS '学生评价均分(1-5)';
COMMENT ON COLUMN teacher_ratings.completion_rate IS '课程完成率(0-100)';
COMMENT ON COLUMN teacher_ratings.total_students IS '总学员数';
COMMENT ON COLUMN teacher_ratings.total_courses IS '总课程数';
COMMENT ON COLUMN teacher_ratings.calculated_at IS '评分计算时间';

CREATE TABLE IF NOT EXISTS teacher_tier_log (
    id              BIGSERIAL    PRIMARY KEY,
    teacher_id      BIGINT       NOT NULL REFERENCES users(id),
    from_tier       VARCHAR(20)  NOT NULL,
    to_tier         VARCHAR(20)  NOT NULL,
    reason          VARCHAR(500),
    triggered_by    VARCHAR(50)  NOT NULL DEFAULT 'CRON',
    operator_id     BIGINT       REFERENCES users(id),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  teacher_tier_log IS '教师等级变更记录';
COMMENT ON COLUMN teacher_tier_log.reason IS '变更原因';
COMMENT ON COLUMN teacher_tier_log.triggered_by IS '触发方式: CRON(定时任务) / ADMIN(手动调整)';

CREATE INDEX IF NOT EXISTS idx_teacher_tier_log_teacher ON teacher_tier_log(teacher_id);
CREATE INDEX IF NOT EXISTS idx_teacher_ratings_tier     ON teacher_ratings(tier);
