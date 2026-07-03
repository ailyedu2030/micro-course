-- V126__fix_proposal_signatures_sign_date_type.sql
-- 修复: proposal_signatures.sign_date 列类型不匹配问题
-- V123 将 sign_date 从 TIMESTAMP 改为 DATE，但 Java Entity 字段类型为 LocalDateTime，
-- 导致读取时 PostgreSQL JDBC 驱动抛出 "Cannot convert the column of type DATE to requested type java.time.LocalDateTime" 异常。
-- 将列类型改回 TIMESTAMP 以匹配 Entity 定义。

ALTER TABLE proposal_signatures
    ALTER COLUMN sign_date TYPE TIMESTAMP USING sign_date::timestamp;
