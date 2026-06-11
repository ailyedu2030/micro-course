-- V3__gate2_courses.sql · Gate 2 课程核心表
-- course_categories / courses / tags / course_tag_relations / course_chapters
-- 依据: docs/数据字典.md v0.5 §2.1-2.6
-- 日期: 2026-06-11

-- 1. course_categories（课程分类表）
CREATE TABLE course_categories (
    id          BIGSERIAL   PRIMARY KEY,
    name        VARCHAR(50) NOT NULL,
    parent_id   BIGINT      REFERENCES course_categories(id) ON DELETE SET NULL,
    level       INTEGER     NOT NULL DEFAULT 1,
    sort_order  INTEGER     NOT NULL DEFAULT 0,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_cc_parent_id ON course_categories(parent_id);

-- 2. courses（课程表）
CREATE TABLE courses (
    id                    BIGSERIAL   PRIMARY KEY,
    title                 VARCHAR(200) NOT NULL,
    subtitle              VARCHAR(500),
    summary               VARCHAR(300),
    cover_url             VARCHAR(500),
    category_id           BIGINT       NOT NULL REFERENCES course_categories(id),
    teacher_id            BIGINT       NOT NULL REFERENCES users(id),
    offer_department_id   BIGINT       REFERENCES departments(id),
    semester              VARCHAR(20),
    credit_hours          DECIMAL(3,1),
    course_nature         VARCHAR(20),
    max_students          INTEGER      DEFAULT 0,
    difficulty            INTEGER      DEFAULT 1,
    status                INTEGER      NOT NULL DEFAULT 0,
    reject_reason         VARCHAR(500),
    description           TEXT,
    student_count         INTEGER      DEFAULT 0,
    avg_rating            DECIMAL(3,2) DEFAULT 0,
    published_at          TIMESTAMP,
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version               INTEGER      NOT NULL DEFAULT 0
);
CREATE INDEX idx_courses_teacher      ON courses(teacher_id);
CREATE INDEX idx_courses_category     ON courses(category_id);
CREATE INDEX idx_courses_status       ON courses(status);
CREATE INDEX idx_courses_published_at ON courses(published_at);

-- 3. tags（标签表）
CREATE TABLE tags (
    id          BIGSERIAL   PRIMARY KEY,
    name        VARCHAR(30) NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tags_name UNIQUE (name)
);

-- 4. course_tag_relations（课程标签关联表）
CREATE TABLE course_tag_relations (
    id          BIGSERIAL   PRIMARY KEY,
    course_id   BIGINT      NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    tag_id      BIGINT      NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    CONSTRAINT uk_ctr_course_tag UNIQUE (course_id, tag_id)
);
CREATE INDEX idx_ctr_course ON course_tag_relations(course_id);
CREATE INDEX idx_ctr_tag    ON course_tag_relations(tag_id);

-- 5. course_chapters（课程章节表）
CREATE TABLE course_chapters (
    id            BIGSERIAL   PRIMARY KEY,
    course_id     BIGINT      NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    title         VARCHAR(200) NOT NULL,
    description   TEXT,
    sort_order    INTEGER     NOT NULL DEFAULT 0,
    chapter_type  VARCHAR(20) NOT NULL DEFAULT 'VIDEO',
    duration      INTEGER     DEFAULT 0,
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version       INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT uk_cc_course_sort UNIQUE (course_id, sort_order)
);
CREATE INDEX idx_cc_course_id ON course_chapters(course_id);
