# 全量回归测试与缺陷闭环验证 (Full-Coverage Redteam Testing & Defect-Closure Verification)

> **OpenSpec Change**: `full-coverage-redteam-testing`
> **Schema**: spec-driven
> **创建日期**: 2026-07-06
> **基于版本**: V1.20.0 (2026-07-04) + V1.19.1 Dashboard 修复 + 2026-07-06 165 项细粒度审计闭环

---

## Why

**问题陈述**: 项目当前在 V1.20.0 版本（2026-07-04 发版），2026-07-06 刚完成 165 项细粒度业务逻辑审计 + P0-P3 全量修复闭环（commit `9ba513e`）。但仍有以下未解风险：

1. **回归风险**: 修复引入新代码，必须验证旧链路未破坏
2. **审计盲区**: 2026-07-06 报告自身指出 4 项 P0 联动缺陷仍"待修"（P0-S02/S03/S04/S05）
3. **退课盲区**: 报告指出 ENR-005/006/008/009 退课相关 4 个操作单元未在审计范围内
4. **未提交改动**: 3 个文件未提交改动（UserList.vue / SecurityConfig.java / StorageApplicationController.java）未在审计范围内
5. **环境/工具链残缺**: 本机 `simdutf` 库缺失、`micro-course-redis-1` 容器 3351 次重启失败（已在本 change 阶段 0 修复）
6. **学校服务器未覆盖**: 生产环境 100.74.122.13 (Tailscale) 暂未纳入测试范围

**为什么现在做**: 项目等成功是第一原则、用户体验是至高原则；刚发版 + 大量修复 = 必须做回归测试 + 验证修复 + 补漏，绝不允许"修了不验"或"修了不知修了没有"。

---

## What Changes

### 新增能力 (New Capabilities)

- **CAP-1 全量回归测试 (full-coverage-regression)**: 按 Vibe Coding 4 维校验 (按钮异常 / 业务常识 / 交互错乱 / 功能残缺) 对 V1.20.0 全 396 个 API + 127 个页面做回归测试
- **CAP-2 缺陷闭环验证 (defect-closure-verification)**: 对 2026-07-06 审计中 165 项缺陷逐一回放,验证"已修"项真修、找出"未修"项
- **CAP-3 联动缺陷复测 (linkage-defect-recheck)**: 对 LD-002/LD-005/LD-006/LD-009/LD-011/LD-015 6 个高危联动缺陷做纵向串联复测
- **CAP-4 退课链路补审 (enrollment-withdraw-audit)**: 补做 ENR-005/006/008/009 4 个退课相关操作单元,修补审计盲区
- **CAP-5 环境修复 (environment-repair)**: 修复本机工具链残缺 (simdutf 库) + Docker 配置残缺 (redis 容器 requirepass 空密码)

### 修改能力 (Modified Capabilities)

- **CAP-6 (修改) 权限矩阵**: 审计新发现的权限注解缺失/错配需更新 `docs/权限矩阵.md` v2.x
- **CAP-7 (修改) 业务逻辑规范**: 补全状态机/订单/支付/审批链路规范到 `docs/状态机设计.md` / `docs/开发规范.md`
- **CAP-8 (修改) API 契约**: 补全/修正审计新发现的接口契约到 `docs/API契约-Phase1.md` v1.3

---

## Capabilities

### New Capabilities

- `full-coverage-regression`: 全量回归测试 (V1.20.0 全部功能)
- `defect-closure-verification`: 165 项缺陷闭环验证 (2026-07-06 审计)
- `linkage-defect-recheck`: 6 个高危联动缺陷纵向复测
- `enrollment-withdraw-audit`: 退课链路补审 (4 个操作单元)
- `environment-repair`: 工具链 + Docker 环境修复

### Modified Capabilities

- `permission-matrix`: docs/权限矩阵.md v2.0 → v2.x (按新发现增删改)
- `business-logic-spec`: docs/状态机设计.md / docs/开发规范.md 补全
- `api-contract`: docs/API契约-Phase1.md v1.2 → v1.3 补全

---

## Impact

### 影响代码域 (跨 6 大域,符合"跨 3 域以上需标注"规则)

1. **后端 Java 域** (Spring Boot 3): 62 Controller / ~80 Service / ~120 Mapper / 25 Security 配置
2. **前端 Vue 域** (Vue 3 + Element Plus): 127 页面 + 80+ 组件 + 完整路由 + Pinia store
3. **数据库域** (PostgreSQL 17.5 + Flyway): ~80 张表 + ~50 迁移文件
4. **中间件域** (Redis 7): 登录失败计数 + Token 黑名单 + 会话缓存
5. **运维域** (Docker Compose + Nginx): 4 容器编排 + 反向代理
6. **测试域** (JUnit 5 + Playwright MCP): 既有测试套件 + 新增回归测试

### 影响系统

- ✅ **本机开发环境** (Mac mini, macOS 26.5.1, 100.69.175.105): 全量测试主战场
- ⏳ **学校生产服务器** (100.74.122.13 via Tailscale): **暂不动** — 等老板授权通道后单独开摊
- ⏳ **生产域名 microcourse.ailyedu.cn** (118.89.201.33): **不动** — 不在 Tailscale 网络

### 影响数据

- ✅ **本机 PostgreSQL test 容器** (5433 端口): 可读可写,用于测试数据准备
- ✅ **本机 Redis test 容器** (6380 端口): 可读可写,用于 Redis 逻辑测试
- ❌ **生产数据库**: 不动
- ❌ **任何带 PII 的真实数据**: 不动 (只造测试数据)

### 影响人员

- **测试执行**: 阿福 (AI Agent, commander profile, MiniMax-M3)
- **协调/决策**: 老板 (jackie)
- **审批/发布**: 等同 V1.20.0 流程

### 时间影响

- 不设死线 — 按 OpenSpec 4 步完整走完
- 不设 token 预算 — 项目成功第一,用户体验至上
- 不设"试点/分阶段" — 老板明确要求"按部就班,不遗漏"

### 风险

| 风险 | 等级 | 缓解 |
|---|---|---|
| 修改生产配置 | 0 | 本任务只动本机,不动学校服务器 |
| 引入新 BUG | 低 | 每个修复后跑回归 + commit + 验证 |
| 测试覆盖不全 | 中 | 严格按 4 维校验,不允许合并测试单元 |
| 性能问题 | 低 | 本任务聚焦业务逻辑,不深度性能 |
| OpenSpec 工具链残缺 | 0 | 已在阶段 0 修复 simdutf |

---

## 前置条件 (OpenSpec rules 强制要求)

1. ✅ 已读 OpenSpec `AGENTS.md` + 4 个 skill (explore/propose/apply/archive)
2. ✅ 已读项目宪法 `.claude/skills/microcourse/SKILL.md` + 6 份 references
3. ✅ 已读 `docs/开发规划/phase15-storage-application-spec.md` (当前 Phase 15)
4. ✅ 已读 `docs/开发规划/phase14-micro-specialty-spec.md` (前序 Phase 14)
5. ✅ 已读 `docs/审计/项目业务逻辑审计-2026-07-06-细粒度/` (现有审计基线)
6. ✅ 已读 `CHANGELOG.md` V1.20.0 (当前发版基线)
7. ✅ 已读 `AGENTS.md` 生产环境保护铁律 (不碰 100.74.122.13)
8. ✅ 已修复 OpenSpec CLI 残缺 (simdutf symlink)

## 引用 (OpenSpec rules 强制要求)

- **当前 Phase**: Phase 15 (storage-application) + Phase 14 (micro-specialty) + V1.20.0 (Phase 11 互动课程插件)
- **现有审计基线**: 2026-07-06 `docs/审计/项目业务逻辑审计-2026-07-06-细粒度/06-项目整体业务逻辑整改总表.md`
  - 总缺陷 165 项 (去重 120 项)
  - P0 = 10, P1-C = 39, P1-I = 47, P2 = 69
- **既有 12 Agent 报告**: `docs/审计/.../03-Agent审查报告/Agent{1-12}-Report.md` (15,791 行)
  - **本任务不参考** (老板明确要求"从头开始,不允许参考别人找出的问题")

## 反向依赖 (本 change 完成后会更新)

- `docs/数据字典.md` v0.5 → v0.6 (如发现新表/新字段)
- `docs/API契约-Phase1.md` v1.2 → v1.3 (如发现新接口/新错误码)
- `docs/权限矩阵.md` v2.0 → v2.x (如发现新权限注解问题)
- `docs/状态机设计.md` v1.0 → v1.x (如发现新状态机问题)
- `docs/开发规范.md` v1.4 → v1.5 (如发现新规范违反)
