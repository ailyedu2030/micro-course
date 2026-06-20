-- V48__course_type_and_price.sql
-- Phase 11.0: 插件基础设施
-- ① courses 表新增课程类型、价格、免费标记
-- ② 新建 plugin_grants 表（插件授权）

ALTER TABLE courses
    ADD COLUMN IF NOT EXISTS course_type VARCHAR(20) NOT NULL DEFAULT 'VIDEO',
    ADD COLUMN IF NOT EXISTS price DECIMAL(10,2),
    ADD COLUMN IF NOT EXISTS is_free BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX IF NOT EXISTS idx_courses_course_type ON courses(course_type);

CREATE TABLE IF NOT EXISTS plugin_grants (
    id BIGSERIAL PRIMARY KEY,
    plugin_id VARCHAR(50) NOT NULL,
    grant_type VARCHAR(20) NOT NULL,
    grantee_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_plugin_grants UNIQUE (plugin_id, grant_type, grantee_id)
);

CREATE INDEX IF NOT EXISTS idx_plugin_grants_plugin ON plugin_grants(plugin_id);
