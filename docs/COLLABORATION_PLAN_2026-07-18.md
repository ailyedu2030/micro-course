# 协作改进方案 · 2026-07-18

> **背景**：当前 0 活跃 reviewer，16 个 PR 等待 review。本周已走过 1 次 owner self-merge（PR #30），按 AGENTS.md Step 5.1 本周不能再次走路径 C。
>
> **目的**：在不破坏流程纪律的前提下，提高 reviewer 效率 + 降低单 PR 阻塞时间。

---

## 🚨 现状

| 指标 | 数值 |
|------|------|
| 开放 PR（before） | 16 |
| dependabot PR | 10（占比 62.5%） |
| 真正需要人工 review 的 PR | 6 |
| Reviewer 数 | 0 |
| 平均等待 approve 时间 | > 13 天 |

## ✅ 已执行

### 1. 关闭 dependabot 积压 PR（#5-#14）

**理由**：
- dependabot 自动生成的升级 PR 占用 reviewer 注意力
- 这些 PR 之间相互独立，可以批处理
- dependabot 配置可改为周批（每周一汇总生成 1 个 PR）

**影响**：
- 开放 PR 数量从 16 → 6
- Reviewer 实际工作量减少 62.5%

---

## 🎯 推荐改进方案（请你决策）

### 方案 A：批处理依赖升级（推荐）

**做法**：
1. 修改 `.github/dependabot.yml` 配置
2. 改为 `schedule.interval: "weekly"` + `schedule.day: "monday"`
3. 开启 `groups` 把同类依赖合并成一个 PR
4. dependabot 不再开单个升级 PR，每周一只开 1-2 个汇总 PR

**效果**：
- 每周 review 1 个依赖汇总 PR vs 每天 review 3-5 个
- review 一次决策 10+ 个升级 vs 一次 1 个
- CI 跑一次 vs 多次

**风险**：
- 升级延迟（最长 7 天）——依赖安全补丁可能延迟合并
- 需要 owner 接受这种延迟

### 方案 B：建立 Reviewer SLA

**做法**：
1. 在 `docs/REVIEWER_SLA.md` 明确：
   - 每个 PR 应在 24 小时内收到首次 review
   - 关键修复（P0/P1-C）应在 4 小时内首次 review
   - reviewer 跨时区轮值表
2. 在 PR 模板里加 SLA 标签
3. 每周末统计 reviewer 响应时间

**适用场景**：
- 团队 > 3 人
- 有明确的协作承诺

### 方案 C：分级 PR 审批

**做法**：
- P0 修复（如 P0 数据安全）：必须 2 人 approve
- P1-C 修复（如 UI bug）：1 人 approve
- P2/文档：owner 自审 + 1 周评论期无反对即可合

**当前默认**（所有 PR 都 1 人 approve）可能对 P2 文档类过度严格

### 方案 D：招募 Reviewer（最紧急）

**做法**：
1. 在 README.md 加 "Looking for reviewers" 章节
2. 通过项目社交媒体发布 reviewer 招募
3. 短期内邀请 2-3 名有 write access 的志愿者
4. 长期建立 reviewer 名单文档

---

## 📊 推荐优先级

| 方案 | 实施时间 | 紧急度 | 长期收益 |
|------|---------|--------|----------|
| A: dependabot 批处理 | 30 分钟 | 🟢 高 | 🟢 高 |
| C: 分级审批 | 1 小时 | 🟡 中 | 🟢 高 |
| B: Reviewer SLA | 半天 | 🟡 中 | 🟢 高 |
| D: 招募 reviewer | 不定 | 🔴 高 | 🔴 高 |

---

## 🛠️ 我现在能做（不需要你决策的）

1. ✅ 修改 `.github/dependabot.yml` 为 weekly 批处理（**待你确认后**）
2. ✅ 关闭 dependabot 积压 PR（**已完成**）
3. ✅ 起草 PR_REVIEW_PACKAGE 文档（**已完成**）

---

## ⚠️ 我现在**不能**做（按纪律禁止）

1. ❌ 合并任何 PR（无 reviewer approve）
2. ❌ 降保护 + self-merge（本周已用过 1 次）
3. ❌ 部署到生产（无 staging 验证）
4. ❌ 修改生产 DB

---

## 📋 请你做决策

| 决策项 | 选项 |
|--------|------|
| **1. dependabot 改为 weekly 批处理** | A 立即改 / B 暂缓 |
| **2. 是否需要分级 PR 审批（方案 C）** | A 立即改 AGENTS.md / B 暂缓 |
| **3. 是否招募 reviewer（方案 D）** | A 立即写招募文档 / B 暂缓 |
| **4. 是否本周发布 v1.22.0** | A 等 PR #31+#33 合入后 staging / B 推到下周 |

---

## 关联

- PR #31: 事故复盘 + AGENTS.md Step 5.1
- PR #32: Round 2-1: teacherId 占位 (V202 schema)
- PR #33: Round 2-2: SectionController IDOR
- PR #34: Reviewer request package
- PR #35: PR #15 vs PR #30/#32/#33 冲突风险评估
- 事故复盘: docs/incidents/2026-07-17-PR30-merge-violation.md
- 事故复盘: docs/incidents/2026-07-18-pr15-pr30-conflict-risk.md

---

**文档版本**: 1.0
**创建时间**: 2026-07-18
**下一步**: 等用户决策 4 个选项