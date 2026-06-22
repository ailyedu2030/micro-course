-- V28__attachments.sql · 统一附件管理表（多态关联）
-- 依据: docs/数据字典.md v0.5 §10.1

CREATE TABLE IF NOT EXISTS attachments (
    id               BIGSERIAL      PRIMARY KEY,
    attachable_type  VARCHAR(50)     NOT NULL,
    attachable_id    BIGINT          NOT NULL,
    file_name        VARCHAR(255)    NOT NULL,
    file_size        BIGINT          NOT NULL,
    file_md5         VARCHAR(64),
    mime_type        VARCHAR(100)    NOT NULL,
    url              VARCHAR(500)    NOT NULL,
    storage_path     VARCHAR(500)    NOT NULL,
    acl              VARCHAR(20)     NOT NULL DEFAULT 'PRIVATE',
    expires_at       TIMESTAMP,
    sort_order       INTEGER         NOT NULL DEFAULT 0,
    uploader_id      BIGINT,
    created_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_att_type_id ON attachments(attachable_type, attachable_id);
CREATE INDEX IF NOT EXISTS idx_att_uploader ON attachments(uploader_id);