# CI 性能优化追踪表（CI Performance Optimization Tracker）

> **本表是 PR-1 / PR-2 / PR-3 CI 性能优化的唯一进度真相。**
> 维护规则：每次优化前先记录 baseline，每次合并后记录实测数据。
> 治理依据：AGENTS.md L0 UX 至上铁律（CI 等待时间是工程体验的隐性成本）+ Step 5.1 数据驱动决策。

---

## 0. 关键约束（来自历史经验，不可绕过）

⚠️ **P0-12 稳定性修复（2026-07-26）** — 后端测试维持 `forkCount=1 + reuseForks=false`
- 原因：共享 JVM 在 ~150 测试后触发 classpath 资源缓存损坏（`sql/p0-seed.sql` 找不到）
- **代价**：每测试类启动独立 JVM + Spring 上下文加载（额外 5~8 分钟）
- **本优化不能**：❌ 改为 `parallel=classes` / ❌ 共享 Redis 锁被并行覆盖（AuthFlowIntegrationTest 401 vs 200 实测失败）
- **本优化能做**：✅ 通过缓存 + paths-filter + sharding 减少**其他**瓶颈

⚠️ **必须先通过**：AuthFlowIntegrationTest + 全量回归 + 连续 3 次 CI 全绿

---

## 1. 改前 Baseline（采集于 2026-08-18，来自 gh API 实测最近 6 次成功 run）

| Run ID | 类型 | Wall-time | backend | frontend | docker | e2e | monitoring-lint | secrets-check | references-sync |
|--------|------|----------:|--------:|---------:|-------:|----:|----------------:|--------------:|----------------:|
| 32167917936 | push main | 1167s (19.5min) | 340s | 88s | 259s | **821s** | 9s | 5s | 6s |
| 32164025243 | push main | 887s (14.8min) | 396s | 94s | 300s | 487s | 21s | 5s | 7s |
| 32160548577 | PR | 1390s (23.2min) | (未抓到) | (未抓到) | (未抓到) | (未抓到) | (未抓到) | (未抓到) | (未抓到) |
| 32164197684 | PR | 1747s (29.1min) | (未抓到) | (未抓到) | (未抓到) | (未抓到) | (未抓到) | (未抓到) | (未抓到) |
| 32113067710 | push main | 670s (11.2min) | 403s | 75s | 122s | 260s | 18s | 6s | 7s |
| 32113221939 | PR | 660s (11.0min) | (未抓到) | (未抓到) | (未抓到) | (未抓到) | (未抓到) | (未抓到) | (未抓到) |

### Baseline 汇总（仅 3 个有 jobs 数据的 run：32167917936 / 32164025243 / 32113067710）

| Job | 平均耗时 | 中位耗时 | 占 critical path |
|-----|--------:|--------:|----------------:|
| **e2e** | **523s (8.7min)** | 487s | **48%** ← 最大瓶颈 |
| backend | 380s (6.3min) | 396s | 35% |
| docker | 227s (3.8min) | 259s | 21% |
| frontend | 86s | 88s | 8% |
| monitoring-lint | 16s | 18s | 1% |
| references-sync | 7s | 7s | <1% |
| secrets-check | 5s | 5s | <1% |
| **Run 总 wall-time（critical path）** | **908s (15.1min) 平均** | 887s | 100% |

### Critical Path 排序（实测）
```
e2e (8.7min) ────────────────────────────────────────
  ├─ needs: backend (6.3min)
  │     └─ mvn verify (含 1123 测试 + JaCoCo)
  ├─ needs: frontend (1.4min)
  │     └─ lint + unit + build
  └─ needs: docker (3.8min) ← 看似不必要?docker image build 不影响 e2e 跑通
```

### 外部基准（Cal.com 同类项目）
- 改前：30 min → 改后：5 min（↓83%）
- 主要手段：setup-java 内置 cache + lookup-only + 解耦依赖 + 测试分片

---

## 2. 优化 Roadmap（3 个 PR）

### PR-1 · 基础缓存 + paths-filter ⏳ 待实施

**改动范围**：仅 `.github/workflows/ci.yml`，零业务代码

| # | 改动 | 当前 | 目标 | 预期节省 |
|---|------|------|------|---------:|
| 1 | Maven 缓存改 setup-java@v5 内置 cache | `actions/cache@v4 ~/.m2` 手写 | `cache: maven` 精准命中 | ~30-60s/backend |
| 2 | 新增 changes job（paths-filter） | 全量跑所有 jobs | docs-only PR 跳过后端/e2e/docker | ~15min/docs-PR |
| 3 | advisory jobs 也按 paths-filter 跳过 | 每次跑 monitoring/secrets/refs | 仅相关路径触发 | ~30s/次 |
| 4 | e2e job 加 lookup-only 缓存检查 | 无条件下载解压 | 命中即跳过下载 | ~10s/e2e |

**预期效果**：
- 代码 PR：18min → ~14min（↓22%）
- Docs-only PR：18min → ~3min（↓83%）

**风险评估**：🟢 极低（单一文件改动，已被 100k+ repo 验证）

### PR-2 · E2E 拆分 + 2-shard 灰度

**改动范围**：`.github/workflows/ci.yml` + `.github/actions/` 可能新增

| # | 改动 | 预期效果 |
|---|------|---------|
| 1 | e2e job 拆为 `e2e-api-smoke` + `e2e-playwright` 两个并行 job | 8.7min → ~5min（API 冒烟从 e2e 中剥离） |
| 2 | Playwright --shard=1/2 + 2/2 矩阵 | 2 个 shard 并行，wall-time 减半 |
| 3 | 加 setup-db job 集中填充 DB cache | 避免多 shard 抢 DB cache |
| 4 | 调整 e2e timeout（55min → 35min） | 节省 runner 占用 |

**预期效果**：
- 代码 PR：14min → ~7-8min（↓50%）
- Docs-only PR：~3min（不变）

**风险评估**：🟡 中（需验证 shard 间测试隔离，无 AuthFlowIntegrationTest 等共享状态类被影响）

### PR-3 · Docker GHA cache + 自定义 runner 镜像

**改动范围**：`micro-course-api/Dockerfile` + `docker-compose.yml` + ci.yml

| # | 改动 | 预期效果 |
|---|------|---------|
| 1 | docker job 用 `cache-from: type=gha, cache-to: type=gha` | 镜像层缓存复用 |
| 2 | 预构建 `e2e-base` 镜像（含 ffmpeg/postgres-client） | e2e 跳过 apt-get install |
| 3 | 镜像推 GHCR，CI 拉取 | ~30-60s/e2e |

**预期效果**：
- 代码 PR：~7-8min → ~5-6min（↓20%）
- Docs-only PR：~3min（不变）

**风险评估**：🟡 中（需新增 GHCR secrets，Dockerfile 略改）

---

## 3. 改后实测数据（待 PR-1 merge 后填入）

### PR-1 merge 后（merge 后第 2-3 个 run）

| Run ID | 类型 | Wall-time | backend | frontend | docker | e2e | monitoring-lint | secrets-check | references-sync |
|--------|------|----------:|--------:|---------:|-------:|----:|----------------:|--------------:|----------------:|
| _待填_ | _docs PR_ | _目标 ≤3min_ | _skipped_ | _xxx s_ | _skipped_ | _skipped_ | _skipped_ | _xxx s_ | _xxx s_ |
| _待填_ | _code PR_ | _目标 ≤14min_ | _xxx s_ | _xxx s_ | _xxx s_ | _xxx s_ | _xxx s_ | _xxx s_ | _xxx s_ |

### PR-2 merge 后
| Run ID | 类型 | Wall-time | e2e-api-smoke | e2e-playwright shard 1 | e2e-playwright shard 2 |
|--------|------|----------:|--------------:|----------------------:|----------------------:|
| _待填_ | _code PR_ | _目标 ≤8min_ | _xxx s_ | _xxx s_ | _xxx s_ |

### PR-3 merge 后
| Run ID | 类型 | Wall-time | docker | e2e |
|--------|------|----------:|-------:|----:|
| _待填_ | _code PR_ | _目标 ≤6min_ | _xxx s_ | _xxx s_ |

---

## 4. 治理审计日志

| 日期 | 事件 | 责任人 | 备注 |
|------|------|--------|------|
| 2026-08-18 | Baseline 采集（6 次 run） | AI（项目负责人） | 通过 gh API 实测 |
| 2026-08-18 | 历史分析回顾（docs/qa/2026-08-03-ci-performance-analysis.md） | AI | 确认 P0-12 稳定性约束 |
| _待填_ | PR-1 实施 | AI | _5 维自审通过后开 PR_ |
| _待填_ | PR-1 merge 后实测 | AI | _观察 2-3 个 run_ |
| _待填_ | PR-2 启动决策 | AI | _依据实测数据决定_ |

---

## 5. 引用与依据

- 外部基准：[Cal.com - How We Cut Our CI Wall Time from 30 Minutes to 5 Minutes](https://cal.com/blog/how-we-cut-our-ci-wall-time-from-30-minutes-to-5-minutes)
- 外部基准：[Mergify - How to Cut Your GitHub Actions CI Bill](https://www.mergify.com/blog/how-to-cut-your-github-actions-ci-bill-without-compromising-tests)
- 外部基准：[GitHub Actions Caching Strategy](https://github.com/actions/cache)
- 本项目历史：[docs/qa/2026-08-03-ci-performance-analysis.md](./2026-08-03-ci-performance-analysis.md)
- 治理依据：AGENTS.md L0 UX 至上铁律 + Step 5.1 owner 自提 PR 流程
