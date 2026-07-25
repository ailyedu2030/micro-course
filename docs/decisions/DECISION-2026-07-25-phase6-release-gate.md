# DECISION-2026-07-25 · Phase 6 教师模块发布门禁决策

> 决策日期：2026-07-25
> 决策角色：AI 总工程师 / 项目执行负责人
> 适用范围：Phase 6 教师模块候选发布
> 关联主线提交：执行时以 `origin/main` HEAD 为准

---

## 一、决策结论

**结论：允许进入 staging，暂不允许直接 production 部署。**

当前版本可被认定为 `Release Candidate`，已经达到“发布准备完成、允许进入 staging 人工验证”的标准；但在取得 staging 实证结果前，不批准跳过 staging 直接进入 production gray 或 production 全量。

---

## 二、证据链

### 2.1 代码与主线状态

- PR #123 已完成教师模块功能收口并 squash merge 到 `main`
- PR #124 已完成发布交接包与 staging 材料回写并 squash merge 到 `main`
- PR #125 已完成发布状态同步并 squash merge 到 `main`
- 当前主线提交：执行 staging 前以 `origin/main` HEAD 为准，并在 execution record 中回填实际部署提交
- 本地 `main` 与 `origin/main` 已同步，发布交接分支已清理

### 2.2 质量门禁证据

| 项目 | 证据 |
|---|---|
| 前端全量单测 | `110/110` 通过 |
| 后端定向测试 | `TeacherPendingTasksConsistencyTest 1/1` 通过 |
| `npm run lint` | 通过 |
| `npm run build` | 通过 |
| `mvn -DskipTests compile` | 通过 |
| 本地隔离部署 | `local-dev-deploy.sh --keep` `16/16` 通过 |
| GitHub CI | `backend / frontend / e2e / docker / monitoring-lint` 全绿 |
| `deploy-gate` | 门禁窗口有效 |
| `deploy-dryrun` | staging / prod 均 `0 fail / 10 warn` |

### 2.3 发布材料证据

- `CHANGELOG.md` 已补齐当前候选发布记录
- `ROLLBACK_PLAN.md` 已补齐当前候选发布回滚说明
- `docs/deferred-items.md` 已登记允许延期项
- staging 人工执行清单已建立

---

## 三、为什么不直接放 production

### 3.1 当前缺的不是代码，而是环境实证

当前代码质量、CI 和本地隔离验证都已达标，但仍缺少以下生产前证据：

1. staging 环境真实部署结果
2. staging 核心教师链路实测结果
3. staging 5 分钟观察结果
4. 若继续推进 production gray，还需要 white-list 与监控观察结果

### 3.2 历史事故约束

根据项目生产安全铁律和既有事故复盘：

- 不允许跳过 staging 直接推进生产
- 不允许 AI 代替人工执行真实 staging / production 容器与环境操作
- 不允许没有实证就做“应该没问题”的生产放行

---

## 四、允许延期但不阻断 staging 的事项

| 条目 | 等级 | 当前结论 |
|---|---|---|
| `vendor-el` 大包体 warning | P2 | 不阻断 staging，转入性能治理专题 |
| `Entity-数据字典漂移` advisory | P1-I（历史） | 不阻断 staging，转入数据契约治理专题 |

---

## 五、进入 staging 的执行要求

进入 staging 前，必须满足：

1. 由项目负责人或运维人工执行
2. 使用独立 staging 环境，明确不是生产
3. 先备份当前 staging 后端 / 前端 / 数据库
4. 严格按清单执行：
   - [docs/releases/2026-07-25-phase6-teacher-staging-checklist.md](file:///Users/jackie/微课平台/docs/releases/2026-07-25-phase6-teacher-staging-checklist.md)

---

## 六、下一决策点

人工完成 staging 后，下一次总工决策分两种：

### A. staging 通过

- 可以进入 production gray 准备
- 再决定是否加入白名单用户、执行 5 分钟灰度监控

### B. staging 不通过

- 停止所有 production 讨论
- 回到修复 -> 本地验证 -> CI -> 再次 staging

---

## 七、最终判断

**本轮候选发布的当前正式状态：**

- `可继续`：staging
- `不可继续`：直接 production
- `当前等待`：项目负责人 / 运维人工执行 staging，并回填 `docs/releases/2026-07-25-phase6-teacher-staging-execution-record.md`

这不是保守拖延，而是基于当前证据链做出的有效放行判断。
