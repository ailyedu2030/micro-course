-- V82__micro_specialties.sql
-- Phase 14: 微专业（MicroSpecialty）核心 6 张表
-- 顺序：micro_specialties → micro_specialty_courses → micro_specialty_teachers → micro_specialty_enrollments
-- 注：micro_specialty_proposals / micro_specialty_featured_audit 在 V84 中

-- ============================================================
-- 1. micro_specialties — 微专业主表
-- ============================================================
CREATE TABLE micro_specialties (
    id                          BIGSERIAL PRIMARY KEY,
    code                        VARCHAR(30)  NOT NULL,
    title                       VARCHAR(200) NOT NULL,
    subtitle                    VARCHAR(500),
    cover_url                   VARCHAR(500),
    description                 TEXT,
    offer_department_id         BIGINT       NOT NULL REFERENCES departments(id) ON DELETE RESTRICT,
    lead_teacher_id             BIGINT       NOT NULL REFERENCES users(id)       ON DELETE RESTRICT,
    target_audience             TEXT,
    training_objective          TEXT,
    admission_requirement       TEXT,
    completion_rule             VARCHAR(20)  NOT NULL DEFAULT 'ALL_REQUIRED',
    total_credits               NUMERIC(6,2) NOT NULL DEFAULT 0,
    total_hours                 INTEGER      NOT NULL DEFAULT 0,
    required_course_count       INTEGER      NOT NULL DEFAULT 0,
    elective_course_count       INTEGER      NOT NULL DEFAULT 0,
    min_credits                 NUMERIC(6,2) NOT NULL DEFAULT 0,
    max_students                INTEGER      NOT NULL DEFAULT 0,
    student_count               INTEGER      NOT NULL DEFAULT 0,
    semester                    VARCHAR(20),
    is_featured                 BOOLEAN      NOT NULL DEFAULT FALSE,
    featured_rank               INTEGER      NOT NULL DEFAULT 0,
    featured_status             VARCHAR(20)  NOT NULL DEFAULT 'NONE',
    featured_apply_at           TIMESTAMP,
    featured_apply_reason       VARCHAR(500),
    featured_approved_by        BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    featured_approved_at        TIMESTAMP,
    is_gold_featured            BOOLEAN      NOT NULL DEFAULT FALSE,
    gold_featured_by            BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    gold_featured_at            TIMESTAMP,
    status                      VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    reject_reason               VARCHAR(500),
    submitted_at                TIMESTAMP,
    approved_at                 TIMESTAMP,
    opened_at                   TIMESTAMP,
    closed_at                   TIMESTAMP,
    creator_id                  BIGINT       NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at                  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version                     INTEGER      NOT NULL DEFAULT 0,
    deleted_at                  TIMESTAMP
);

CREATE UNIQUE INDEX uk_ms_code  ON micro_specialties(code) WHERE deleted_at IS NULL;
CREATE INDEX        idx_ms_dept      ON micro_specialties(offer_department_id) WHERE deleted_at IS NULL;
CREATE INDEX        idx_ms_status    ON micro_specialties(status)             WHERE deleted_at IS NULL;
CREATE INDEX        idx_ms_lead      ON micro_specialties(lead_teacher_id)     WHERE deleted_at IS NULL;
CREATE INDEX        idx_ms_semester  ON micro_specialties(semester)            WHERE deleted_at IS NULL;
CREATE INDEX        idx_ms_featured  ON micro_specialties(is_featured, featured_rank)
    WHERE is_featured = TRUE AND deleted_at IS NULL;

-- ============================================================
-- 2. micro_specialty_courses — 课程编排
-- ============================================================
CREATE TABLE micro_specialty_courses (
    id                  BIGSERIAL PRIMARY KEY,
    micro_specialty_id  BIGINT         NOT NULL REFERENCES micro_specialties(id) ON DELETE CASCADE,
    course_id           BIGINT         NOT NULL REFERENCES courses(id)          ON DELETE RESTRICT,
    sort_order          INTEGER        NOT NULL DEFAULT 0,
    is_required         BOOLEAN        NOT NULL DEFAULT TRUE,
    credits             NUMERIC(6,2)   NOT NULL DEFAULT 0,
    hours               INTEGER        NOT NULL DEFAULT 0,
    min_score           NUMERIC(5,2)   NOT NULL DEFAULT 60,
    recommended_semester VARCHAR(20),
    created_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_msc_unique ON micro_specialty_courses(micro_specialty_id, course_id);
CREATE INDEX        idx_msc_ms     ON micro_specialty_courses(micro_specialty_id);
CREATE INDEX        idx_msc_course ON micro_specialty_courses(course_id);

-- ============================================================
-- 3. micro_specialty_teachers — 教师团队
-- ============================================================
CREATE TABLE micro_specialty_teachers (
    id                  BIGSERIAL PRIMARY KEY,
    micro_specialty_id  BIGINT       NOT NULL REFERENCES micro_specialties(id) ON DELETE CASCADE,
    teacher_id          BIGINT       NOT NULL REFERENCES users(id)            ON DELETE RESTRICT,
    role                VARCHAR(20)  NOT NULL,
    course_id           BIGINT       REFERENCES courses(id) ON DELETE SET NULL,
    responsibility      VARCHAR(500),
    invite_status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    invited_by          BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    invited_at          TIMESTAMP,
    responded_at        TIMESTAMP,
    invite_expires_at   TIMESTAMP,
    joined_at           TIMESTAMP,
    left_at             TIMESTAMP,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 排除已退/已拒绝的活跃唯一索引，支持重新邀请
CREATE UNIQUE INDEX uk_mst_active
    ON micro_specialty_teachers(micro_specialty_id, teacher_id, course_id)
    WHERE invite_status NOT IN ('DECLINED', 'REMOVED');
CREATE INDEX idx_mst_ms            ON micro_specialty_teachers(micro_specialty_id);
CREATE INDEX idx_mst_teacher       ON micro_specialty_teachers(teacher_id);
CREATE INDEX idx_mst_role          ON micro_specialty_teachers(role);
CREATE INDEX idx_mst_invite_status ON micro_specialty_teachers(invite_status);

-- DB 触发器：每个微专业有且仅有 1 名 ACTIVE LEAD
CREATE OR REPLACE FUNCTION trg_ms_one_lead_fn()
RETURNS TRIGGER AS $$
DECLARE
    active_lead_count INTEGER;
BEGIN
    IF NEW.role = 'LEAD' AND NEW.invite_status = 'ACTIVE' THEN
        SELECT COUNT(*) INTO active_lead_count
        FROM micro_specialty_teachers
        WHERE micro_specialty_id = NEW.micro_specialty_id
          AND role = 'LEAD'
          AND invite_status = 'ACTIVE'
          AND id <> COALESCE(NEW.id, -1);
        IF active_lead_count >= 1 THEN
            RAISE EXCEPTION 'micro_specialty % already has an ACTIVE LEAD', NEW.micro_specialty_id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_ms_one_lead ON micro_specialty_teachers;
CREATE TRIGGER trg_ms_one_lead
    BEFORE INSERT OR UPDATE ON micro_specialty_teachers
    FOR EACH ROW EXECUTE FUNCTION trg_ms_one_lead_fn();

-- ============================================================
-- 4. micro_specialty_enrollments — 修读记录
-- ============================================================
CREATE TABLE micro_specialty_enrollments (
    id                  BIGSERIAL PRIMARY KEY,
    micro_specialty_id  BIGINT         NOT NULL REFERENCES micro_specialties(id) ON DELETE CASCADE,
    user_id             BIGINT         NOT NULL REFERENCES users(id)            ON DELETE CASCADE,
    source              VARCHAR(20)    NOT NULL,
    class_id            BIGINT         REFERENCES classes(id) ON DELETE SET NULL,
    status              VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    progress            NUMERIC(5,2)   NOT NULL DEFAULT 0,
    credits_earned      NUMERIC(6,2)   NOT NULL DEFAULT 0,
    courses_completed   INTEGER        NOT NULL DEFAULT 0,
    courses_required    INTEGER        NOT NULL DEFAULT 0,
    final_score         NUMERIC(5,2),
    final_grade         VARCHAR(20),
    certificate_id      BIGINT         REFERENCES certificates(id) ON DELETE SET NULL,
    pending_courses     JSONB          NOT NULL DEFAULT '[]'::jsonb,
    applied_at          TIMESTAMP,
    approved_at         TIMESTAMP,
    approved_by         BIGINT         REFERENCES users(id) ON DELETE SET NULL,
    completed_at        TIMESTAMP,
    dropped_at          TIMESTAMP,
    drop_reason         VARCHAR(500),
    created_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version             INTEGER        NOT NULL DEFAULT 0
);

-- 排除已退/已拒/已失败的活跃唯一索引，支持重新申请
CREATE UNIQUE INDEX uk_mse_active
    ON micro_specialty_enrollments(micro_specialty_id, user_id)
    WHERE status NOT IN ('REJECTED', 'DROPPED', 'FAILED');
CREATE INDEX idx_mse_user   ON micro_specialty_enrollments(user_id);
CREATE INDEX idx_mse_ms     ON micro_specialty_enrollments(micro_specialty_id);
CREATE INDEX idx_mse_status ON micro_specialty_enrollments(status);
CREATE INDEX idx_mse_class  ON micro_specialty_enrollments(class_id);
