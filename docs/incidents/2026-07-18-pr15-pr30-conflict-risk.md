# PR #15 vs PR #30/#32/#33 冲突风险评估

## 风险等级

🔴 **P0 — 必须立即处理**

## 背景

- PR #15 (`fix/business-logic-audit-165-defects`) 165 项审计修复，base = main 的旧版本（早于 PR #30）
- PR #30 已 squash merge 到 main，包含 OrderServiceImpl 套餐购买逻辑修复
- PR #32 (Round 2-1) 与 PR #33 (Round 2-2) 修复 teacherId 占位 + SectionController IDOR
- PR #15 修了相同文件但**基于 PR #30 之前的版本**

## 冲突文件清单

| 文件 | PR #15 的修改 | PR #30 修复 | 冲突类型 |
|------|--------------|------------|---------|
| `micro-course-api/.../OrderServiceImpl.java` | 删除 `CourseBundle` import + 删套餐分支 | 加回 `CourseBundle` import + 加套餐分支 | 🔴 **覆盖修复** |
| `micro-course-admin/.../MicroSpecialtyProposal.vue` | 修了 5 步表单 | 修了 toggleChapter teacherId 占位 | 🔴 后续合并冲突 |
| `micro-course-api/.../StorageApplicationCudServiceImpl.java` | 未审 | Round 2-1 加 teacherId 校验 | 🟡 可能冲突 |

## 风险场景

### 场景 1：PR #15 直接合入 main（不 rebase）

- 会**完全覆盖** PR #30 的套餐购买修复
- 触发 v1.22.0 客户可感知 bug 回归
- 套餐购买访问权问题（必修课 vs 全部课程）重新出现
- 套餐退款原子性回退到原 buggy 状态

### 场景 2：PR #15 rebase 到 main 之后

- 需要 PR #15 作者或 reviewer 重新整理 165 项修复中与 PR #30 重叠的部分
- 工期 +2-3 天
- 可能发现 PR #15 已经修复了 PR #30 相同的问题（重叠修复）

### 场景 3：PR #15 关掉，仅保留 Round 1+2 的修复

- 165 项审计中**与 PR #30 重叠**的部分，重复实现可丢弃
- **不重叠**的修复，需要分批重新提 PR
- 推荐方案

## 建议

按总工程师放行纪律 + "挑战式放行"：

| 行动 | 决定 | 责任 |
|------|------|------|
| PR #15 暂时不能合入 | ❌ block | reviewer |
| 给 PR #15 加 review comment 提示冲突 | ✅ 立即 | 我 |
| PR #15 作者 rebase 到 main | ⏳ 等 | PR #15 作者 |
| PR #30/#32/#33 优先合入（无 schema 变更的先合） | ✅ 等 reviewer | reviewer |
| PR #32（含 V202 schema） staging 验证后再合 | ⏳ 等 staging | reviewer |

## 我的具体操作

1. 在 PR #15 加 review comment：
   - "⚠️ 本 PR 与 PR #30/#32/#33 有直接文件冲突"
   - "OrderServiceImpl.java 修改会覆盖 PR #30 已修复的套餐购买 bug"
   - "建议先 rebase 到最新 main 或拆分重提"

2. 起草 PR #15 拆分建议：
   - 仅保留与 PR #30 不重叠的修复（~120 项）
   - 关闭 PR #15，新建多个小 PR

## 时间线

- **2026-07-17 21:45**: PR #30 merge 到 main（d34c0e51）
- **2026-07-18 06:30**: Round 2 审计开始
- **2026-07-18 09:10**: 发现 Round 2-1 teacherId 占位 bug
- **2026-07-18 10:30**: PR #32 提交（基于 main，6280dad8 之后）
- **2026-07-18 11:00**: Round 2-2 SectionController IDOR
- **2026-07-18 12:00**: PR #33 提交
- **2026-07-18 14:00**: 发现 PR #15 与 PR #30/#32/#33 冲突

## 关联

- PR #15: https://github.com/ailyedu2030/micro-course/pull/15
- PR #30: https://github.com/ailyedu2030/micro-course/pull/30 (已 merge)
- PR #32: https://github.com/ailyedu2030/micro-course/pull/32
- PR #33: https://github.com/ailyedu2030/micro-course/pull/33
- 事故复盘: docs/incidents/2026-07-17-PR30-merge-violation.md

---

**文档版本**: 1.0
**创建时间**: 2026-07-18
**下一步**: 立即给 PR #15 加 review comment