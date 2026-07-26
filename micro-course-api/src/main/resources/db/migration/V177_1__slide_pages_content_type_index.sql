-- V177_1: V177 的索引补偿迁移(重命名自 V177b 以兼容 Flyway)
-- 原 V177b 用 "b" 后缀导致 Flyway 不识别(默认 V 版本号格式不支持字母后缀)
-- 现在用 V177_1 形式,让 Flyway 自动识别并应用
-- 内容使用 IF NOT EXISTS 保证幂等(可重复执行安全)
--
-- 注意: PostgreSQL CREATE INDEX 不支持事务,但 Flyway 默认在事务中执行
-- IF NOT EXISTS 会在事务回滚时报错(SQL State 25001),但因为是 CREATE 不是 ALTER
-- 实际上 Flyway 的事务包裹对 CREATE INDEX 是允许的(只是 CONCURRENTLY 不允许)
-- 因此本迁移使用普通 CREATE INDEX(不用 CONCURRENTLY),保证 Flyway 事务兼容

CREATE INDEX IF NOT EXISTS idx_slide_pages_content_type ON slide_pages(content_type);
