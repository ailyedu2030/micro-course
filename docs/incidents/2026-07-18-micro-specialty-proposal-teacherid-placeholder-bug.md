# 事故复盘 · 2026-07-18 微专业申报章节分配 teacherId 占位 bug

## 事故概要

- **时间**: 2026-07-18 06:30 ~ 07:50 (CST)（审计发现到修复完成）
- **影响范围**: `micro_specialty_proposals` 关联的 `chapter_teacher_assignments` 表
- **业务影响**: 章节-教师分配数据错位 → inviteTeacher 邀请流程发错对象（中等 P1-C）
- **当前状态**: 已修复，待 PR review + staging 验证

## 根因分析

### 直接原因

[MicroSpecialtyProposal.vue 第 604 行](file:///Users/jackie/微课平台/micro-course-admin/src/views/teacher/MicroSpecialtyProposal.vue#L598-L610) 用前端数组下标当 `teacherId` 占位：

```javascript
chapterAssignments.value.push({
  courseId, chapterId, 
  teamMemberIndex: memberIndex,
  teacherId: memberIndex + 1  // 用序号占位(提案阶段,文本名)
})
```

### 直接后果

| 后果 | 严重度 |
|------|--------|
| `chapter_teacher_assignments.teacher_id` 指向错误用户 | 高 |
| `inviteTeacher` 按 `teacher_id` 查找邀请时发错对象 | 高 |
| 数据库 `UNIQUE(chapter_id, teacher_id)` 约束可能冲突 | 中 |
| 团队成员数组顺序变化 → teacher_id 全错位 | 高 |

### 根本原因

1. **模型混淆**: `teamMembers` 数据结构是"文本占位条目"（仅姓名/年龄/职称/单位），不是真实教师账户。但代码把它当真实教师处理
2. **后端无校验**: `StorageApplicationCudServiceImpl.replaceSubTables()` 直接接受前端传的 `teacherId`，未做用户存在性和角色校验
3. **DB schema 强约束**: `chapter_teacher_assignments.teacher_id` 列 `NOT NULL REFERENCES users(id)`，迫使前端必须填一个 teacherId（即使没有真实教师），是反推力导致占位 bug

### 横向扫描

| 模式 | 检查结果 |
|------|---------|
| 教师模块其他 vue 文件是否有类似占位 | ❌ 仅此一处 |
| `MicroSpecialtyServiceImpl.inviteTeacher()` 是否校验 teacherId | ❌ 仅校验角色和重复，未校验用户存在性 |
| 其他 `chapter_teacher_assignments` 写入路径 | 1 处（`MicroSpecialtyServiceImpl:556` 走 inviteTeacher 流程，teacherId 由前端 user picker 选定，是真实 ID，不受影响）|

## 修复方案

### 1. DB schema 变更（V202）

- `teacher_id` 改为可空（允许"尚未绑定真实教师"占位条目）
- `chk_cta_source_consistency` 增加 `source='TBD' AND teacher_id IS NULL` 分支
- 历史脏数据自动修正：`source='TBD' + accept_status='PENDING' + teacher_id != proposer_id` → `teacher_id = NULL`

### 2. 后端校验

- `ChapterAssignmentItem.teacherId` 去掉 `@NotNull`（允许 null）
- `StorageApplicationCudServiceImpl.replaceSubTables()` 对非 null teacherId 加校验：
  - 用户必须存在
  - 用户角色必须是 `TEACHER`
- 错误：`chapterAssignments.teacherId=X 不存在` 或 `不是教师角色`

### 3. 前端修复

- `toggleChapter()` 写入 `source='TBD', acceptStatus='PENDING'`，**不写 teacherId 字段**
- `buildSavePayload()` 强制清除 chapterAssignments 中的 teacherId

## 防止再发

### 已实施（V202）

1. ✅ 后端对非 null teacherId 强制校验存在性 + 角色
2. ✅ DB 允许 teacher_id NULL（占位条目合法）
3. ✅ 历史脏数据自动修正
4. ✅ 4 个单元测试覆盖（不存在/非教师角色/null/正常路径）

### 推荐改进（下一版本）

1. **前端改进**: `teamMembers` 模型明确区分 `type: 'EXISTING' | 'PLACEHOLDER'`，后者不参与章节分配（仅占位用）
2. **预检规则**: precheck.sh 增加 `check-no-memberindex-teacherid.sh` 扫描前端代码，禁止 `teacherId.*memberIndex` 模式
3. **审计日志**: V202 后端 Service 写入 chapter_teacher_assignments 时记录 `proposer_id` 到日志，便于追溯

## Rollback

```bash
# V202 是 schema 变更，回滚需要:
# 1. 删除 V202 迁移（开发期）
git revert <V202-commit> --no-edit
# 2. 生产回滚
# 风险: V202 已修正脏数据,revert 会让 teacher_id=NULL 的行无法恢复
# 推荐: 如果 V202 在 staging 已成功,但生产有问题,保留 V202,前端代码 revert
```

## 修复证据

| 验证 | 结果 |
|------|------|
| 编译 | ✅ `mvn -q -DskipTests compile` 通过 |
| V202 迁移脚本验证 | ✅ 本地 test DB 重置后,`mvn test` 跑 601 测试全绿 |
| 单元测试新增 | ✅ `ChapterAssignmentTeacherIdValidationTest` 4 个测试 |
| 历史脏数据修正 | ✅ V202 SQL 自动执行（基于 proposer_id 启发式）|
| 前端打包 | ⏳ 待 PR review 后跑 |

## 关联

- PR: 待创建（Round 2 micro-specialty proposal teacherId 修复）
- 事故关联: 与 `2026-07-17-PR30-merge-violation.md` 同期发现，但事故性质不同（这次是 P1-C 业务 bug，不是流程合规）
- CHANGELOG: v1.23.0 待发布

---

**事故定级**: P1-C（业务可感知，但已有 fix 方案）
**修复状态**: ✅ 代码完成，待 PR + staging 验证