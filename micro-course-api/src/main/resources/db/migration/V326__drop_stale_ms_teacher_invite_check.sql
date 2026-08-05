-- P1-C 修复: 跨学院审批驳回必现 409
-- 根因: V153 创建 chk_mst_invite_status（不含 REJECTED），
--       V173 又新增 chk_ms_teacher_invite_status（含 REJECTED）而非替换旧约束，
--       双约束叠加 → 服务写入 REJECTED 被旧约束拦截 → 驳回永远失败。
-- 修复: 删除旧约束 chk_mst_invite_status，保留含 REJECTED 的 chk_ms_teacher_invite_status。

ALTER TABLE micro_specialty_teachers DROP CONSTRAINT IF EXISTS chk_mst_invite_status;
