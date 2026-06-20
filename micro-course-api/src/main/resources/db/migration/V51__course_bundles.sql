-- V51__course_bundles.sql
-- Phase 13: 课程套件（考证课程）

CREATE TABLE IF NOT EXISTS course_bundles (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    cover_url VARCHAR(500),
    creator_id BIGINT NOT NULL REFERENCES users(id),
    price DECIMAL(10,2),
    is_free BOOLEAN NOT NULL DEFAULT TRUE,
    student_count INTEGER NOT NULL DEFAULT 0,
    status INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cb_creator ON course_bundles(creator_id);
CREATE INDEX IF NOT EXISTS idx_cb_status ON course_bundles(status);

CREATE TABLE IF NOT EXISTS course_bundle_items (
    id BIGSERIAL PRIMARY KEY,
    bundle_id BIGINT NOT NULL REFERENCES course_bundles(id),
    course_id BIGINT NOT NULL REFERENCES courses(id),
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_required BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_cbi_bundle_course ON course_bundle_items(bundle_id, course_id);
CREATE INDEX IF NOT EXISTS idx_cbi_bundle ON course_bundle_items(bundle_id);
CREATE INDEX IF NOT EXISTS idx_cbi_course ON course_bundle_items(course_id);
