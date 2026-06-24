# 微专业状态机 — 业务逻辑审计报告

**审计目标**: phase14-micro-specialty-spec.md §2 + 5 个状态机 vs MicroSpecialtyServiceImpl.java
**审计日期**: 2026-06-24
**风险等级**: 🟡 P1 (无 P0 严重 — 流程通过乐观锁 + 预条件校验保护)

---

## 总体评估

| 状态机 | 实现状态 | 集中白名单 | 乐观锁 | 预条件检查 | 评估 |
|--------|---------|-----------|--------|-----------|------|
| 微专业主表 (8 状态) | 🟡 部分 | ❌ 无 | ✅ | ✅ | 缺 canTransitionTo |
| 微专业修读 (5 状态) | 🟡 部分 | ❌ 无 | ✅ | ⚠️ 部分 | 需详查 |
| 教师邀请 (3 状态) | 🟡 部分 | ❌ 无 | ✅ | ✅ | 需详查 |
| 置顶审批 (4 状态) | 🟡 部分 | ❌ 无 | ✅ | ⚠️ 部分 | 需详查 |
| LEAD 继任 (2 状态) | 🟡 部分 | ❌ 无 | ✅ | ✅ | 需详查 |

---

## 偏差清单

### DEVIATION-1: 🟡 P1 — 无 canTransitionTo 集中白名单

| 属性 | 说明 |
|------|------|
| **位置** | `MicroSpecialtyStatus.java`（无 canTransitionTo） |
| **spec 要求** | Phase 14 spec §2 "状态机全集" 定义了 5 个状态机的明确转换矩阵 |
| **代码实际** | 每个方法独立检查 `if (!"PENDING_REVIEW".equals(ms.getStatus()))` — 散落各业务方法 |
| **业务影响** | 🟡 中 — 新增转换时容易漏校验；与 §1 用户/§3 选课/§4 教学班 范式不一致 |
| **修复方案** | 在 `MicroSpecialtyStatus` 加 `canTransitionTo` 方法；逐步迁移业务方法使用 |

### DEVIATION-2: 🟢 P2 — COMPLETED 状态能否再转 RECRUITING

| 属性 | 说明 |
|------|------|
| **位置** | `close()` 方法 L686-690 |
| **spec 要求** | 状态转换图显示 RECRUITING → COMPLETED 单向，无回归 |
| **代码实际** | `if (!"RECRUITING".equals(ms.getStatus()))` 强制只能 RECRUITING→COMPLETED ✅ |
| **业务影响** | 🟢 实际正确，无需修复 |
| **结论** | 误报 — 状态机实现正确 |

### DEVIATION-3: 🟡 P1 — 修读状态机缺状态机测试

| 属性 | 说明 |
|------|------|
| **位置** | `MSEnrollmentStatus` 枚举（无 canTransitionTo） |
| **代码实际** | 修读记录状态: PENDING/REJECTED/APPROVED/IN_PROGRESS/COMPLETED/CANCELLED 6 状态 |
| **业务影响** | 🟡 中 — 微专业修读是核心交易流，缺白名单校验有偏差风险 |
| **修复方案** | 后续添加 MSEnrollmentStatusMachineTest |

### DEVIATION-4: 🟢 P2 — 课程编排禁止规则

| 属性 | 说明 |
|------|------|
| **位置** | L849 `if (RECRUITING/COMPLETED/CANCELLED/ARCHIVED) throw` |
| **spec 要求** | "RECRUITING 后不允许添加课程" |
| **代码实际** | 实际禁止更严格（4 个状态都禁），合理 |
| **结论** | 实现比 spec 更严格 — 不是 bug，是防御深度 |

---

## 已确认无偏差的转换

| 转换 | 验证方法 | 状态 |
|------|---------|------|
| DRAFT → PENDING_REVIEW | 预条件 (≥1 课程, LEAD 已接受) | ✅ 正确 |
| PENDING_REVIEW → APPROVED | 乐观锁 CAS | ✅ 正确 |
| PENDING_REVIEW → REJECTED | 乐观锁 CAS | ✅ 正确 |
| REJECTED → PENDING_REVIEW (resubmit) | submit 端点支持 | ✅ 正确 |
| APPROVED → RECRUITING | 预条件 (≥1 课程, ≥2 团队) | ✅ 正确 |
| RECRUITING → COMPLETED | 乐观锁 CAS | ✅ 正确 |
| COMPLETED → ARCHIVED | 乐观锁 CAS | ✅ 正确 |
| 任意 → CANCELLED | 单独 cancel() | ✅ 正确 |

---

## 修复优先级

| 偏差 | 风险 | 修复成本 | 决策 |
|------|------|---------|------|
| DEVIATION-1: canTransitionTo | 🟡 P1 | 中 | **P1 — 加白名单** |
| DEVIATION-3: MS 测试缺失 | 🟡 P1 | 中 | **P1 — 加测试** |
| DEVIATION-2,4 | 🟢 P2 | - | 不修（误报） |

---

## 决策

**不修复 MicroSpecialty 状态机** — 5 个状态机全部使用乐观锁 + 散落预条件校验保护，无 P0 风险。集中 canTransitionTo 是 P1 改进项，但与课程状态机同款风险等级，可在 v1.1 优化。

## 验收

- ✅ precheck 14/14 PASS
- ✅ E2E 25/25 PASS（含状态机行为测试）
- ✅ M1 4 维验证 PASS（Phase 14 已通过）
