# PR-2 E2E Sharding 设计 + 实施计划

> 创建: 2026-08-20
> 作者: 总工程师
> 状态: 设计完成, 实施 pending (留 PR-2.1 实施 + PR-2.2 完整 sharding)

## 1. 背景

按 docs/qa/ci-optimization-tracker.md §2 路线图, PR-2 目标: e2e 拆分 + 2-shard 灰度, 进一步降低 CI 时间。

## 2. 当前 e2e 状态 (PR-1 优化后)

| Run | Wall-time | e2e | backend | frontend | docker | monitoring-lint | secrets-check | references-sync |
|-----|-----------|-----|---------|----------|--------|-----------------|---------------|------------------|
| 32247275893 (PR-1 实测) | 673s (11.2min) | 241s (4.0min) | 415s | 98s | skipped | skipped | 7s | 6s |

**e2e 当前 241s (4min)** — 是总 wall-time 第二大瓶颈 (仅次于 backend 415s = 6.9min)。

## 3. PR-2 拆解方案

### 3.1 e2e 内部结构 (当前)
1. Setup: JDK + Node + services (PostgreSQL + Redis) + Build JAR + Start Backend + Build Frontend + Start Frontend Preview
2. Tests: API 冒烟 (bash scripts/e2e-test.sh) + Playwright (npx playwright test)
3. Upload artifact (failure only)

### 3.2 拆分设计

**PR-2.1 (chore): 注释 + 文档 (本 PR)**
- 不动 ci.yml 结构, 只添加 PR-2 roadmap 注释
- 准备后续 PR-2.2 完整实施

**PR-2.2 (feat): 完整 e2e sharding**
- 新增 `e2e-api-smoke` job: 快速 API 冒烟 (与 e2e 并行, <2min, 不依赖 frontend build)
- e2e 改为: API 冒烟 + Playwright 2-shard (matrix 拆)
- 预期收益: e2e 总时间 4min → 2min (50% 降低)
- 风险: Playwright shard 隔离 (AuthFlowIntegrationTest 等共享 Redis 锁的测试需确认)
- 完整方案: matrix: { shard: [1, 2] } + shardIndex/shardTotal env vars
- 实施: 参考 tracker §2 PR-2 详细设计

### 3.3 预期收益
- e2e 4min → 2min (50% 降低)
- 总 CI: 11.2min → 9.2min (18% 降低)
- docs-only PR 仍然 SKIPPED (PR-1 设计)
- 仅 backend/frontend 代码变更触发 e2e

## 4. 实施计划

| PR | 内容 | 风险 | 预期收益 |
|----|------|------|---------|
| **PR-2.1 (本)** | 设计文档 + 注释 | 🟢 零 | 文档化 PR-2 路线 |
| PR-2.2 (后续) | 完整 e2e sharding | 🟡 中 (Playwright shard 隔离) | e2e 50% 降低 |
| PR-2.3 (后续) | e2e-api-smoke 独立 job | 🟢 低 | 快速反馈 (<2min) |

## 5. P0-12 兼容性

⚠️ 维持 forkCount=1 + reuseForks=true + heap 3g (PR #271 已优化)。
新增 e2e-api-smoke 不影响 backend 共享 JVM 设计。

## 6. 风险评估

| 风险 | 影响 | 缓解 |
|------|------|------|
| Playwright shard 隔离 (共享 Redis 锁) | 中 | PR-2.2 实施前先在本地 dry-run 验证 (AuthFlowIntegrationTest 等) |
| matrix 增加 CI 复杂度 | 低 | 加注释 + README |
| 现有 e2e tests 假设单机 | 低 | Playwright 1.40+ shard 原生支持 |

## 7. 后续 (PR-3 Docker GHA cache)

PR-3 独立项目, 不影响 PR-2 实施: docker job 用 cache-from: type=gha + 预构建 e2e-base 镜像。

## 8. 关联治理

- R6 precheck (PR #267): workflows outputs id 检查
- R7 precheck (PR #273): 模块顶层 window/document/localStorage 检查
- 后续 R8: 扫描 package.json/pom.xml MAJOR 落后版本 (依赖此 PR 设计)
