-- P1-2026-08-21: 微专业申报章节-团队分配持久化 teamMemberIndex
-- 根因: 前端申报页按"团队成员序号"分配章节(teamMemberIndex), 后端无此字段 → 保存即丢失,
--       重载时 (teacherId||1)-1 映射错乱(占位 teacherId=null 全部归第 1 位成员)
-- 修复: chapter_teacher_assignments 增加 member_index 列, 申报阶段占位条目也可还原所属成员
ALTER TABLE chapter_teacher_assignments
    ADD COLUMN IF NOT EXISTS member_index INTEGER;
