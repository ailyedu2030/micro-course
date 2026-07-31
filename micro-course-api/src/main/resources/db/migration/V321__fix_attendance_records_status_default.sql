-- 【P1C-2 修复 2026-07-30】
-- 根因: V128__attendance_records.sql 注释将 status 枚举写为 PRESENT/LATE/ABSENT/EXCUSED，
--   但数据字典（v1.7 之前）将其写为 PRESENT/LATE/LEAVE/ABSENT（LEAVE 应为 EXCUSED）。
--   此外 V128 DEFAULT 实际为 'ABSENT'（见 DDL DEFAULT 'ABSENT'），注释无需修改。
-- 修复：将现有 'PRESENT' 默认值行的 status 改为 'ABSENT'（仅影响使用了 DEFAULT 的未指定状态行）
--   同时统一值域: LEAVE → EXCUSED（与 V166 CHECK 约束保持一致）
-- 注意：已有明确状态值的行不受影响
UPDATE attendance_records SET status = 'ABSENT' WHERE status = 'PRESENT';
UPDATE attendance_records SET status = 'EXCUSED' WHERE status = 'LEAVE';
