-- =============================================================================
-- V1__init.sql
-- -----------------------------------------------------------------------------
-- 微课平台 Phase 1 初始化脚本
-- 范围：4 张核心表（users / departments / majors / classes）
-- 依据：docs/数据字典.md v0.4 + 冲突评审决议 C5（表名 users 无前缀）
--
-- 命名约定：
--   - 表名：snake_case，无 sys_ 前缀
--   - 字段：snake_case
--   - 主键：id BIGSERIAL
--   - 索引：idx_xxx / uk_xxx
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. users（用户表）
-- -----------------------------------------------------------------------------
CREATE TABLE users (
    id              BIGSERIAL       PRIMARY KEY,
    username        VARCHAR(50)     NOT NULL,
    password        VARCHAR(255)    NOT NULL,
    real_name       VARCHAR(50)     NOT NULL,
    email           VARCHAR(100),
    phone           VARCHAR(30),
    gender          VARCHAR(10),
    avatar          VARCHAR(500),
    role            VARCHAR(20)     NOT NULL,
    department_id   BIGINT,
    major_id        BIGINT,
    class_id        BIGINT,
    grade           VARCHAR(10),
    enrollment_year VARCHAR(10),
    graduation_year VARCHAR(10),
    political_status VARCHAR(20),
    student_no      VARCHAR(30),
    teacher_no      VARCHAR(30),
    status          INTEGER         NOT NULL DEFAULT 1,
    cas_bound       BOOLEAN         NOT NULL DEFAULT FALSE,
    last_login_at   TIMESTAMP,
    deleted_at      TIMESTAMP,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_username   UNIQUE (username),
    CONSTRAINT uk_users_student_no UNIQUE (student_no),
    CONSTRAINT uk_users_teacher_no UNIQUE (teacher_no)
);

CREATE INDEX idx_users_role       ON users (role);
CREATE INDEX idx_users_department ON users (department_id);
CREATE INDEX idx_users_major      ON users (major_id);
CREATE INDEX idx_users_class      ON users (class_id);
CREATE INDEX idx_users_deleted    ON users (deleted_at);

COMMENT ON TABLE  users             IS '用户表：学生/教师/管理员/教务处统一存储，通过 role 区分';
COMMENT ON COLUMN users.password    IS 'bcrypt 加密存储';
COMMENT ON COLUMN users.real_name   IS '真实姓名（API 脱敏返回）';
COMMENT ON COLUMN users.email       IS '邮箱（API 脱敏返回）';
COMMENT ON COLUMN users.phone       IS '手机号（API 脱敏返回）';
COMMENT ON COLUMN users.status      IS '0=INACTIVE, 1=ACTIVE, 2=DISABLED, 3=DELETED';

-- -----------------------------------------------------------------------------
-- 2. departments（院系表）
-- -----------------------------------------------------------------------------
CREATE TABLE departments (
    id          BIGSERIAL       PRIMARY KEY,
    name        VARCHAR(100)    NOT NULL,
    code        VARCHAR(30)     NOT NULL,
    parent_id   BIGINT,
    sort_order  INTEGER         NOT NULL DEFAULT 0,
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_departments_code UNIQUE (code),
    CONSTRAINT fk_departments_parent FOREIGN KEY (parent_id)
        REFERENCES departments (id) ON DELETE SET NULL
);

CREATE INDEX idx_departments_parent ON departments (parent_id);

COMMENT ON TABLE departments IS '院系/学院表';

-- -----------------------------------------------------------------------------
-- 3. majors（专业表）
-- -----------------------------------------------------------------------------
CREATE TABLE majors (
    id              BIGSERIAL       PRIMARY KEY,
    name            VARCHAR(100)    NOT NULL,
    code            VARCHAR(30)     NOT NULL,
    department_id   BIGINT          NOT NULL,
    sort_order      INTEGER         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_majors_code UNIQUE (code),
    CONSTRAINT fk_majors_department FOREIGN KEY (department_id)
        REFERENCES departments (id) ON DELETE RESTRICT
);

CREATE INDEX idx_majors_department ON majors (department_id);

COMMENT ON TABLE majors IS '专业表';

-- -----------------------------------------------------------------------------
-- 4. classes（班级表）
-- -----------------------------------------------------------------------------
CREATE TABLE classes (
    id              BIGSERIAL       PRIMARY KEY,
    name            VARCHAR(50)     NOT NULL,
    major_id        BIGINT          NOT NULL,
    grade           VARCHAR(10)     NOT NULL,
    counselor_id    BIGINT,
    sort_order      INTEGER         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_classes_major FOREIGN KEY (major_id)
        REFERENCES majors (id) ON DELETE RESTRICT,
    CONSTRAINT fk_classes_counselor FOREIGN KEY (counselor_id)
        REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_classes_major_grade ON classes (major_id, grade);
CREATE INDEX idx_classes_name        ON classes (name);
CREATE INDEX idx_classes_counselor   ON classes (counselor_id);

COMMENT ON TABLE classes IS '班级表';

-- -----------------------------------------------------------------------------
-- 5. users 表外键（放在最后，因为其他表先建好才能建外键）
-- -----------------------------------------------------------------------------
ALTER TABLE users
    ADD CONSTRAINT fk_users_department FOREIGN KEY (department_id)
        REFERENCES departments (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_users_major FOREIGN KEY (major_id)
        REFERENCES majors (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_users_class FOREIGN KEY (class_id)
        REFERENCES classes (id) ON DELETE SET NULL;

-- -----------------------------------------------------------------------------
-- 6. 初始数据：1 个 admin + 1 个根院系
-- -----------------------------------------------------------------------------
INSERT INTO users (
    username, password, real_name, email, role, status, cas_bound,
    created_at, updated_at
) VALUES (
    'admin',
    '$2b$12$lSQy6WeZnpZzW4/7yAE4D..V0lhf3JIPGA9nyw0sYGL2EvvAM5h7C',
    '系统管理员',
    'admin@microcourse.local',
    'ADMIN',
    1,
    FALSE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO departments (
    name, code, sort_order, created_at, updated_at
) VALUES (
    '系统根院系',
    'ROOT',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- =============================================================================
-- End of V1__init.sql
-- =============================================================================
