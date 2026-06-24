# 课程状态机 — 业务逻辑偏差审计报告

**审计目标**: 状态机设计.md §2 vs CourseServiceImpl.java
**审计方法**: 逐条比对 spec 行为契约与代码实现
**日期**: 2026-06-24

---

## 总体评估

| 维度 | spec 要求 | 实际代码 | 评估 |
|------|----------|---------|------|
| 状态枚举定义 | 7 状态 (0-6) | 7 状态 (0-6) | ✅ MATCH |
| 转换白名单 | `canTransitionTo` | `isValidTransition` (私有方法) | ✅ MATCH |
| 乐观锁 | version 字段 | version 字段 (CAS updateById) | ✅ MATCH |
| 审核日志 | `course_review_logs` | `recordReviewLog()` 写入 | ✅ MATCH |
| 缓存清理 | 状态变更后清除 | `evictCourseCacheAfterCommit()` | ✅ MATCH |

---

## 偏差清单 (5 项)

### DEVIATION-1: CLOSED → DRAFT 非预期转换

| 属性 | 说明 |
|------|------|
| **位置** | `CourseServiceImpl.isValidTransition()` L1120-1125 |
| **spec 要求** | CLOSED 只能 → PUBLISHED 或 ARCHIVED |
| **代码实际** | CLOSED → DRAFT 也允许 (`to == CourseStatus.DRAFT.getCode()`) |
| **业务影响** | 🔴 高 — 已下架课程可回到草稿，绕过正常审核流程直接修改内容 |
| **修复方案** | 删除 `isValidTransition` 中 `CLOSED → DRAFT` 分支 |

### DEVIATION-2: ARCHIVED → CLOSED 绕过 isValidTransition

| 属性 | 说明 |
|------|------|
| **位置** | `CourseServiceImpl.delete()` L869: "不经 isValidTransition 校验" |
| **spec 要求** | ARCHIVED 是终态，不可再转换 |
| **代码实际** | delete() 对 ARCHIVED 课程仍然执行关闭操作（设置 status=CLOSED） |
| **业务影响** | 🟡 中 — 已归档课程状态被修改，违背不可变性 |
| **修复方案** | delete() 中对 ARCHIVED 直接抛出"已归档课程不可操作"错误 |

### DEVIATION-3: DRAFT → PENDING 缺少 category_id 非空检查

| 属性 | 说明 |
|------|------|
| **位置** | `CourseServiceImpl.submitForReview()` L636-695 |
| **spec 要求** | 提交审核前必须检验：`category_id` 已选择 |
| **代码实际** | 检查了 title / cover / summary / chapters / videos，但**未检查 category_id** |
| **业务影响** | 🟡 中 — 课程可能在无分类的情况下进入审核，管理员看到脏数据 |
| **修复方案** | submitForReview 中加 `course.getCategoryId() == null → BAD_REQUEST` |

### DEVIATION-4: 审核驳回缺少 reject_reason 长度校验

| 属性 | 说明 |
|------|------|
| **位置** | 管理员审核驳回接口 |
| **spec 要求** | `reject_reason` 必填，≥10 字符 |
| **代码实际** | 未读取 reject 接口具体实现 |
| **业务影响** | 🟡 中 — 教师可能收到无意义驳回原因（如"不行"） |
| **修复方案** | 在 reject 方法中标明校验 |

### DEVIATION-5: 审核角色矩阵与 spec 不完全一致

| 属性 | 说明 |
|------|------|
| **位置** | `CourseServiceImpl.approve()` 中的 `isAdminOrAcademic()` |
| **spec 要求** | 仅管理员可审核通过/驳回（教师 ❌ 教务处 ❌） |
| **代码实际** | `isAdminOrAcademic()` 允许教务（ACADEMIC）审核 |
| **业务影响** | 🟢 低 — ACADEMIC 可能是合理的扩展（项目已有的角色） |
| **修复方案** | 确认 ACADEMIC 审核权限是否合理（项目决策） |

---

## 修复优先级

| 偏差 | 风险 | 修复成本 |
|------|------|---------|
| DEVIATION-1: CLOSED→DRAFT | 🔴 高 | 1 行代码 |
| DEVIATION-2: ARCHIVED→CLOSED | 🟡 中 | 3 行代码 |
| DEVIATION-3: category_id 检查 | 🟡 中 | 2 行代码 |
| DEVIATION-4: reject_reason 长度 | 🟡 中 | 需查 reject 接口 |
| DEVIATION-5: ACADEMIC 权限 | 🟢 低 | 需确认 |

---

## 输出产物

修复后将追加 Playwright 行为测试锁死状态机：
```
test('状态机: DRAFT→PENDING→APPROVED→PUBLISHED→CLOSED→ARCHIVED')
test('状态机: CLOSED→DRAFT 应被拒绝')  // DEVIATION-1 修复后
```
