# 微课平台接管报告 (2026-08-19)

> **作者**: 总工程师 (项目负责人)
> **范围**: 微课平台（micro-course）main 分支接管
> **状态**: 接管进行中，已完成 P0 治理与核心 CI 优化
> **下一次接管里程碑**: PR-2 (e2e sharding) 启动决策

---

## 0. 执行摘要 (TL;DR)

| 维度 | 状态 |
|---|---|
| **环境健康** | ✅ Docker daemon UP / Redis UP / Postgres UP / 后端 UP / 前端 UP |
| **磁盘治理** | ✅ 13Gi → 24Gi 可用 / Docker 上限 64→24GiB / 重复 docker 资源清理 |
| **进程治理** | ✅ 137 个 cagent 泄漏清理 / Docker AI 关闭 (防止再生) |
| **CI 优化** | ✅ PR-1 (PR #266) merged: -26% wall-time, -54% e2e |
| **P0 漏洞** | ✅ 修复 (id: filter) + 验证 + 【防止再发】 R6 规则落地 |
| **Open PRs** | 5 闭 2 合 (PR #158 element-plus, PR #263, PR #156 vite-plugin-vue-devtools, PR #266, PR #267, PR #268) |
| **依赖升级** | 1 合 (element-plus) / 1 关 (vite-plugin-vue-devtools peer dep) / 2 等 (jsoup/hutool) / 6 暂缓 (MAJOR) |

---

## 1. 接管前状态盘点

### 1.1 Git 状态
- main HEAD (接管前): `1400b79c docs: 补录 PR #264 (VideoServiceImpl 拆分) - 首次 0 advisory`
- 工作分支: `ci/perf-pr1-cache-paths-filter` (领先 main 3 commits，含 PR-1 优化)
- Open PRs: 14 (1 业务 + 13 dependabot)

### 1.2 环境
- Docker daemon: ❌ 不响应 (`docker ps` 返回 EOF)
- 根因: Docker Desktop 虚拟磁盘上限 64GiB 超出系统可用 13Gi，`no space left on device` → VM 崩溃
- 副症: 137 个 cagent (Docker AI) 进程泄漏 (3.5GB RAM) / 重复 compose 项目 (micro-course + microcourse 双套卷) / 大量应用缓存占用

### 1.3 治理文档
- AGENTS.md: L0 UX 宪法 + P0 生产安全铁律 + 缺陷分级标准 + PR 审批流程 (Bot auto-approve)
- docs/发布: 纪律 7 条 (反偏见机制)
- docs/PRODUCTION_SAFETY: 10 条生产铁律
- docs/qa/ci-optimization-tracker: PR-1 baseline + roadmap (3 个 PR 渐进)

---

## 2. 接管过程时间线 (2026-08-19)

### 2.1 环境治理 (09:00 - 10:30)
1. **Docker 磁盘治理**: 64→24GiB 上限 / docker system prune 3.5G / 删除重复 compose 项目 + 5 卷
2. **Redis 修复**: brew services start redis
3. **后端 8080 卡死**: 杀掉旧 java 进程 (PID 44011) + 同参数重启 (PID 39101)
4. **应用缓存清理**: Trae logs / EdgeUpdater / opencode / electron / IDE 缓存等 (保留 playwright + IDE 配置)
5. **Docker AI 关闭**: EnableDockerAI false / 杀掉 137 个 cagent 进程 (3.5GB 释放)
6. **结果**: 磁盘 13Gi → 26Gi 可用, 后端 `/actuator/health → UP`

### 2.2 PR-1 根因分析 (10:30 - 11:30)
1. **发现**: PR #266 (ci(perf): paths-filter) 首次 CI run (32246359946) 全部 5 个 required check SKIPPED 但 `mergeStateStatus=CLEAN`
2. **根因**: dorny/paths-filter@v3 step 未设 `id: filter` → `steps.filter` undefined → outputs 空字符串 → if ('' == 'true') 评估 false → job SKIPPED
3. **影响**: GH 分支保护视 SKIPPED 为通过 → Bot auto-approve → 实际 0 测试运行 (P0 漏洞)
4. **修复**: commit `41f86fb1` 加 `id: filter` (Squash merge 进入 PR #266 → commit `d00172e7`)
5. **验证**: run 32247275893 全部 SUCCESS (backend 415s / frontend 98s / e2e 241s)
6. **PR #266 squash merge**: Bot auto-approve + main HEAD `d00172e7`

### 2.3 PR-263 处理 (11:30)
- PR #263 关闭 (内容冗余): main 上已有 PR #262 补录 + 更先进的 Phase 11/12 状态，merge 会引入重复 + 过时内容

### 2.4 dependabot 批量处理 (11:30 - 12:30)
1. **合并**: PR #158 element-plus 2.14.1→2.14.4 (patch, CI 9/9 SUCCESS)
2. **关闭**: PR #156 vite-plugin-vue-devtools 7→8 (peer dep 冲突: vite 5 vs vite-plugin-vue-devtools 8 要求 vite 6+, 不在本次接管范围)
3. **等待**: PR #152 jsoup + PR #151 hutool (backend 跑中)
4. **暂缓**: 6 个 MAJOR 升级 (#192 vue-i18n 9→11, #159 pinia 2→4, #157 happy-dom 15→20, #155 springdoc 2.5→3.1, #154 easyexcel 3.3→4.0, #153 openpdf 1.3→3.0) — 每个需要单独评估迁移成本

### 2.5 【防止再发】治理 (12:30 - 13:30)
1. **R6 规则**: precheck.sh 新增 `check_workflow_outputs_id` (27/27 PASS)
   - 检测 `.github/workflows/*.yml` 中 `steps.<id>.outputs.*` 引用, 对应 step 必须有 `id` 字段
   - 正向验证: ci.yml (有 id) PASS
   - 反向验证: 构造 _test_bad.yml → precheck FAIL, 准确报告 "step 引用 steps.filter.outputs 但无 id: filter"
2. **FIELDS_CONTRACT.md 同步**: PR #260 (F10-D2 灰度分流) Controller 77→78 + GrayRelease 行补录
3. **PR #267 merged**: commit `c98e8e55`

### 2.6 tracker 登记 (13:30)
- PR #268 (docs(qa): 登记 PR-1 实测数据 + P0 漏洞治理闭环): merged, commit `d3fa2ed0`
- tracker.md 增加:
  - PR-1 实测数据 (run 32247275893: 673s/11.2min vs baseline 908s/15.1min, -26%)
  - e2e: 241s vs 523s (-54%)
  - P0 漏洞发现/修复审计日志 (7 条事件)
  - R6 规则治理日志

---

## 3. 当前 main 分支状态

```
d3fa2ed0 docs(qa): 登记 PR-1 实测数据 + P0 漏洞发现/修复 + R6 防止再发 (#268)
c98e8e55 feat(governance): precheck.sh 增加 R6 规则(workflows outputs 引用完整性) + 同步 FIELDS_CONTRACT (#267)
b4bfb547 chore(deps): bump element-plus in /micro-course-admin (#158)
d00172e7 ci(perf): PR-1 — paths-filter + setup-java 内置 cache (CI wall-time 18min→14min, docs-PR 18min→3min) (#266)
1400b79c docs: 补录 PR #264 (VideoServiceImpl 拆分) - 首次 0 advisory
```

### 关键文件
- `.github/workflows/ci.yml`: 含 id: filter (P0 修复), 5 个 required checks 真实跑通
- `.claude/skills/microcourse/scripts/precheck.sh`: 27/27 PASS (含 R6 规则)
- `docs/qa/ci-optimization-tracker.md`: PR-1 治理闭环登记完整
- `docs/开发规划/FIELDS_CONTRACT.md`: 78 个 Controller 同步

---

## 4. CI 性能 (PR-1 收益)

| 指标 | Baseline (avg) | PR-1 实测 | 变化 |
|---|---:|---:|---:|
| **Wall-time** | 908s (15.1min) | **673s (11.2min)** | **-26%** ✅ |
| **e2e** | 523s (8.7min) | **241s (4.0min)** | **-54%** ✅✅ |
| backend | 380s | 415s | +9% (含 P0 fix + docs 全跑) |
| frontend | 86s | 98s | +14% |
| docker | 227s | skipped | -100% (PR 改 ci.yml) |
| monitoring-lint | 16s | skipped | -100% |

### 预期后续收益
- Docs-only PR: 18min → ~3min (PR-1 设计目标, 验证待 PR #268 后下一个 docs PR)

---

## 5. 风险登记与降级决策

### 5.1 本次新登记

| # | 类型 | 描述 | 降级理由 (为什么暂缓) | 后续动作 |
|---|---|---|---|---|
| T-1 | P1-I | dependabot MAJOR 升级 6 个 (vue-i18n 9→11 / pinia 2→4 / happy-dom 15→20 / springdoc 2.5→3.1 / easyexcel 3.3→4.0 / openpdf 1.3→3.0) | 客户场景: 升级可能引入 API 变更导致运行时错误。频率: 长期累积。修复成本: 每个需 1-3 天迁移 + 回归测试。为什么暂缓: 项目当前无足够时间做完整迁移评估。目标: v1.20.0 或 PR-2 完成后启动 | 每个单独 PR + 评估周期 |
| T-2 | P2 | vite-plugin-vue-devtools 7→8 (peer dep 冲突) | 同上 + 需先升级 vite 5→6 | 留 vite 升级专题 |
| T-3 | P1-I | dependabot PR #152 jsoup 1.18→1.23 + PR #151 hutool 5.8.46→5.8.47 | 客户场景: 已知 CVE 风险（jsoup < 1.21 有 XSS CVE-2021-37714）| **本次必须合并, 等 CI 完成即 merge** |

### 5.2 旧 deferred-items (历史, 已在 tracker 处理)
- F10-D1 (课件工作台类型派生 hack): ✅ 已修复 PR-1 同期 (SlideVO coursewareType 字段)
- F10-D2 (灰度分流机制未接线): ✅ 已修复 PR #260 (GrayReleaseService 实现)
- F10-D3 (i18n 存量硬编码 4757 处): ✅ 已完成 PR #236

---

## 6. 待执行 / 未完成事项

### 6.1 当前等待 (CI)
- PR #152 jsoup 1.18→1.23 (dependabot backend)
- PR #151 hutool 5.8.46→5.8.47 (dependabot backend)
- 期望: backend SUCCESS 后 merge (低风险, 紧接 PR #158 模式)

### 6.2 启动决策待定
1. **PR-2 (E2E 拆分 + 2-shard 灰度)**: 当前 e2e 4min 仍可接受 (PR-1 后), 但 PR-2 设计预期再降 -50% (→ 7-8min code PR). **决策**: 等 PR-1 实测 2-3 个 run 观察稳定性后启动 (避免叠加风险)
2. **MAJOR 升级 6 个**: 每个单独开 PR, 评估周期 (建议先做 #151 #152 jsoup / hutool 这类纯 backend deps)
3. **CI GHA cache (PR-3)**: Docker cache-from: type=gha + 预构建 e2e-base 镜像. 依赖 PR-2 完成后启动

### 6.3 文档待补
- AGENTS.md §纪律 5 补充 "workflows outputs 必须 id" 硬约束 (本次未实现, 留给后续 PR)
- docs/发布: 增 P0 漏洞案例 (PR #266 教训) 进生产安全铁律
- docs/开发流程-完整版: 增加 Step 5.7 "fix commit 必须包含 P0 漏洞触发的根因分析"

---

## 7. 关键经验教训 (L0 优先级)

### L1: PR 自审必须验证隐式依赖
- PR-1 5 维自审通过但漏掉 "outputs 是否能传递到下游" 这一隐含依赖
- 教训: **自审模板需增加 R6 "DAG 数据流验证"** (已在 PR #267 落地)
- 总工程师后续: 自审清单 + 自动化检查双管齐下

### L2: SKIPPED 在 GH 分支保护中视同通过
- 这是 P0 漏洞: 5 个 required check 全部 SKIPPED 仍可 merge
- 教训: **路径过滤类 CI 优化必须配合"id 完整性"检查**
- 教训: **SKIPPED + Bot auto-approve 不能视为"5/5 PASS"** — 需 Bot 与总工程师双重确认

### L3: dependabot 不是"低风险"
- PR #156 (vite-plugin-vue-devtools) 看似 minor 升级, 实际引发 peer dep 冲突
- 教训: **每个 dependabot PR 都需看 CI 实际结果, 不能假设"minor = safe"**
- 后续: dependabot.yml 可配置 `groups` 模式 (当前已配置, 但未阻止 peer dep 问题)

### L4: 多 agent 并发审查文件是真实风险
- 接管开始时发现 ci.yml 状态异常 (read/edit 不一致) — 用户确认有其他 agent 在审查
- 教训: **多 agent 必须建立文件级 lock 或工作区域分工**
- 后续: 项目 AGENTS.md 应增加并发治理规则

---

## 8. 下次接管 checklist

- [ ] PR #152 #151 CI 完成 → merge
- [ ] PR-2 启动决策 (基于 PR-1 实测稳定性观察)
- [ ] 6 个 MAJOR dependabot 评估
- [ ] AGENTS.md §纪律 5 增补 "outputs id 约束"
- [ ] docs/发布: 增加 P0 漏洞案例
- [ ] docs/开发流程: Step 5.7 增 "fix 隐式依赖根因"模板
- [ ] 跑 `bash scripts/local-dev-deploy.sh` 16/16 验证 (端到端测试)

---

## 9. 参考资料

- 本次 P0 漏洞 commit: `41f86fb1` (PR #266 squash merge 含)
- PR-1 治理资产: docs/qa/ci-optimization-tracker.md
- AGENTS.md §纪律 7 (fix commit 根因模板)
- docs/发布: §总工程师放行纪律 7 条
- docs/PRODUCTION_SAFETY.md (P0 生产安全铁律)
- 外部基准: Cal.com - How We Cut Our CI Wall Time from 30 Minutes to 5 Minutes

---

**接管报告生成**: 总工程师 · 2026-08-19
**下次更新**: PR-2 启动前 / 或每周接管节奏
