-- =====================================================================
-- V202__chapter_teacher_teacher_id_nullable.sql
-- 修复章节-教师分配 teacher_id 占位 bug
--
-- 【根因】
-- MicroSpecialtyProposal.vue 第 604 行用 `memberIndex + 1` 当作
-- teacherId 写入 chapter_teacher_assignments，导致：
-- 1. teacher_id 字段指向了错误的用户（可能是完全不相关的教师账户）
-- 2. inviteTeacher 流程通过 teacher_id 发邀请时，邀请发给了错误的人
-- 3. 数据脏：UNIQUE(chapter_id, teacher_id) 约束下，不同教师可能冲突
--
-- 【修复】
-- 1. teacher_id 改为可空（允许 NULL = 尚未绑定真实教师）
-- 2. chk_cta_source_consistency 增加新分支：source='TBD' AND teacher_id IS NULL
-- 3. 历史脏数据自动修正：
--    - 当 source='TBD' 且 teacher_id 不为空但与 chapter 无有效绑定时
--      （heuristic: teacher_id 与 proposal 牵头人不同、且 chapter_teacher_assignments 中
--       同一 chapter 有 ACCEPTED 教师时），置 teacher_id=NULL
--    - 简化策略：source='TBD' + accept_status='PENDING' 的记录若 teacher_id 指向非牵头人，
--      则 teacher_id=NULL（让前端重新绑定）
--
-- 【防止再发】
-- - 后端 StorageApplicationCudServiceImpl 接收到 teacher_id=null 时正确处理
-- - 前端 toggleChapter 改为不写 teacher_id 字段（不传）
-- =====================================================================

-- 1. 放宽 teacher_id 允许 NULL（保留外键约束，NULL 时不触发 FK）
ALTER TABLE chapter_teacher_assignments
    ALTER COLUMN teacher_id DROP NOT NULL;

-- 2. 删除旧 UNIQUE 约束（因为现在允许同 chapter 多个 NULL 占位 + 多个真实教师，但同一
--    chapter 不允许同一 teacher 重复——这个语义仍保留，但 NULL 在 SQL 里被视为"互不相等"，
--    所以不需要特殊处理）
--    旧约束: UNIQUE (chapter_id, teacher_id) — 保留即可，因为 NULL 不参与 unique 冲突
--    (PostgreSQL 语义)

-- 3. 替换 chk_cta_source_consistency 增加 TBD+NULL 路径
ALTER TABLE chapter_teacher_assignments DROP CONSTRAINT IF EXISTS chk_cta_source_consistency;
ALTER TABLE chapter_teacher_assignments ADD CONSTRAINT chk_cta_source_consistency CHECK (
    (source = 'existing' AND source_course_id IS NOT NULL AND source_chapter_id IS NOT NULL)
    OR (source = 'new')
    OR (source = 'TBD' AND source_course_id IS NULL AND source_chapter_id IS NULL)
);

-- 4. 历史脏数据修正
-- 启发式: source='TBD' + accept_status='PENDING' 的记录表示尚未邀请接受；
--         若 teacher_id 与该 proposal 的 proposer 不同（说明是 memberIndex+1 占位污染），
--         则 teacher_id=NULL（前端会重新绑定）。
-- 注: proposals 表只有 proposer_id 字段，没有 lead_teacher_id。proposer 是申报人，
-- 通常也是牵头人,这里用 proposer 作为对比基准。
UPDATE chapter_teacher_assignments cta
SET teacher_id = NULL
WHERE source = 'TBD'
  AND accept_status = 'PENDING'
  AND teacher_id IS NOT NULL
  AND EXISTS (
      SELECT 1 FROM micro_specialty_proposals p
      WHERE p.id = cta.proposal_id
        AND p.proposer_id IS DISTINCT FROM cta.teacher_id
  );

-- 5. 审计日志（如果有 audit_log 表）
-- 留空：审计由 Java Service 层在迁移后通过日志主动记录