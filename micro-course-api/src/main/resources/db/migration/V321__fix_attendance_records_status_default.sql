-- 【P1C-2 修复 2026-07-30】
-- 根因: V128__attendance_records.sql 中 status 默认值为 'PRESENT' 但数据字典定义为 'ABSENT'
-- 修复：将现有 'PRESENT' 默认值行的 status 改为 'ABSENT'（仅影响使用了 DEFAULT 的未指定状态行）
-- 注意：已有明确状态值的行不受影响
-- 同时统一值域: LEAVE → EXCUSED（数据字典定义）
UPDATE attendance_records SET status = 'ABSENT' WHERE status = 'PRESENT';
UPDATE attendance_records SET status = 'EXCUSED' WHERE status = 'LEAVE';
