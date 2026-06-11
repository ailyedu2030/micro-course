-- V10__gate3_notifications.sql · 消息通知表
-- 依据: docs/数据字典.md v0.5 §7.1-7.2
-- 日期: 2026-06-11

-- 1. notifications（消息通知表）
CREATE TABLE notifications (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type        VARCHAR(30) NOT NULL,
    title       VARCHAR(200) NOT NULL,
    content     TEXT,
    related_id  BIGINT,
    channel     VARCHAR(20) DEFAULT 'SITE',
    is_read     BOOLEAN     DEFAULT FALSE,
    read_at     TIMESTAMP,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_notif_user    ON notifications(user_id, is_read);
CREATE INDEX idx_notif_created ON notifications(created_at);

-- 2. notification_preferences（通知偏好表）
CREATE TABLE notification_preferences (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE UNIQUE,
    allow_site         BOOLEAN   DEFAULT TRUE,
    allow_email        BOOLEAN   DEFAULT FALSE,
    allow_wechat       BOOLEAN   DEFAULT FALSE,
    quiet_hours_start  VARCHAR(5),
    quiet_hours_end    VARCHAR(5),
    updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_np_user ON notification_preferences(user_id);
