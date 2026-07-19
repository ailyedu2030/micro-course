# 项目治理基线评估报告 · W31 (2026-07-20)

> **签发**: 总工程师 (项目唯一全权负责人)
> **依据**: AGENTS.md + docs/PRODUCTION_SAFETY.md + 用户第 16-20 次授权铁律
> **范围**: 4 大治理方向全覆盖评估

---

## 总评: 现状打分 (10 分制)

| 治理维度 | 现状分 | 目标分 | 缺口 | 优先级 |
|---------|-------|-------|------|--------|
| 1. 技术架构分层 | **8.5** | 10 | 缺可视化分层图 + 模块依赖图谱 + OpenAPI 校验 | P1 |
| 2. 性能监控保障 | **6.0** | 10 | 缺 Prometheus+Grafana 部署 + 真实 10w 并发压测 + 慢查询分析 | P1 |
| 3. 客户体验至上 | **7.0** | 10 | 缺多渠道反馈 + 7 日闭环 + 首屏优化 < 1.5s 实测 | P2 |
| 4. 全流程质量管控 | **7.5** | 10 | 缺单测覆盖率 ≥90% + 双人审核 + 灰度 10/50/100% 阶梯 | P1 |
| **加权总分** | **7.25** | **10** | — | — |

---

## 一、技术架构分层 · 现状 8.5/10

### ✅ 已落地 (W30 成果)
- 4 层架构（表现/业务/数据/基础设施）
- 5 模块职责说明书
- 10 项 ADR (Architecture Decision Record)
- 5 条核心调用链文档
- 接口契约: `FIELDS_CONTRACT.md` 字段级约束

### ❌ 缺口 (需 W31+ 补齐)
| 项 | 当前 | 目标 | 方案 |
|---|------|------|------|
| 架构分层图 | 文本 ASCII | Mermaid 可视化 | A2 阶段 |
| 模块依赖图谱 | 无 | 自动生成 (maven dependency:tree + Graphviz) | A2 阶段 |
| RESTful 规范 | 24 endpoint 命名 | 100% 遵循 + OpenAPI 校验 | A3 阶段 |
| 接口自动化校验 | precheck 22 项 | OpenAPI 契约测试 + springdoc 导出 | A3 阶段 |
| 模块耦合度 | 未量化 | JaCoCo 圈复杂度 < 10 / 类 | D1 阶段 |
| 核心模块独立部署 | 100% | 已达 (Spring Boot 单进程) | ✓ |

---

## 二、性能监控与保障 · 现状 6.0/10

### ✅ 已落地
- 静态 p99 < 200ms 估算 (PPT ~80ms, HTML ~60ms, 音频 ~25ms)
- N+1 SQL 修复 (BUG #9: 30 SQL → 2 SQL)
- Redis 接入 (BUG #29: 流式 GET 缓存)
- HikariCP 连接池优化 (max=100, leak-detection=60s)
- HTML Sanitizer 防 XSS

### ❌ 缺口
| 项 | 当前 | 目标 | 方案 |
|---|------|------|------|
| Prometheus + Grafana | ❌ 无 | 部署完成 + 仪表盘 5+ 张 | B1 阶段 |
| 10w 并发压测 | ❌ 无方案 | JMeter/wrk 脚本 + 报告 | B2 阶段 |
| 慢查询 < 0.1% | ❌ 未监控 | PG `pg_stat_statements` + 报警 | B3 阶段 |
| 音视频卡顿率 < 0.5% | ❌ 无 | 前端 Media API 埋点 | C1 阶段 |
| APM 链路追踪 | 局部 (TraceIdFilter) | SkyWalking/Jaeger 全链路 | B3 阶段 |

---

## 三、客户体验至上 · 现状 7.0/10

### ✅ 已落地
- 3 用户旅程 × 8 阶段 + 痛点优化对照
- 5 项关键指标 (首屏/操作步骤/错误率/满意度/NPS)
- IDOR 防御 (BUG #17/#22/#23) — P0 安全体验
- HtmlSanitizer XSS 防御 — 7-19 P0
- GlobalExceptionHandler 不泄露堆栈 — 安全感

### ❌ 缺口
| 项 | 当前 | 目标 | 方案 |
|---|------|------|------|
| 用户旅程地图 | 文档 | 可视化 (Mermaid journeyDiagram) | C1 阶段 |
| 首屏 < 1.5s | 未实测 | Lighthouse CI + Bundle 拆分 (BUG #35) | C1 阶段 |
| 多渠道反馈 | 工单为主 | 应用内 + 邮件 + 微信 + 工单 4 渠道 | C2 阶段 |
| 反馈 7 日闭环 | 无 SLA | SOP + 看板 + 周报 | C2 阶段 |
| 满意度 ≥4.8/5 | 无 | NPS 调研 + 月度报告 | C2 阶段 |

---

## 四、全流程质量管控 · 现状 7.5/10

### ✅ 已落地
- 5 类测试金字塔 (Unit/Integration/E2E/Regression/Load)
- 单元测试 71 用例 (23 新 + 48 旧) — 100% PASS
- 集成测试 7 用例 (sytafe 验证) — 100% PASS
- precheck 22/22 PASS
- error-patrol 7 阶段 P0=0 P1=0 P2=0
- 21 bug 矩阵 (3 P0 + 6 P1 + 7 P2 + 6 P3)
- 11 项技术债清单

### ❌ 缺口
| 项 | 当前 | 目标 | 方案 |
|---|------|------|------|
| 单测覆盖率 ≥90% | 估算 ~76% (71/85 方法) | JaCoCo 覆盖率报告 + 补齐 | D1 阶段 |
| 双人审核 | 单人 commit | CODEOWNERS + 必须 2 approved | D2 阶段 |
| 灰度 10/50/100% | 无 | mc:feature flag + IP 白名单 | D2 阶段 |
| 缺陷率 ≤0.5/千行 | 未量化 | SonarQube 集成 | D3 阶段 |
| 回归测试 | 仅 PR 前 | CI 自动触发 | D3 阶段 |

---

## 五、W31-W33 治理路线图

### 阶段 A: 架构可视化 (W31 D1-D3)
- [ ] A1: 基线报告 (本文)
- [ ] A2: Mermaid 架构分层图 + 模块依赖图谱 → 嵌入 courseware-architecture.md
- [ ] A3: OpenAPI springdoc 导出 + 契约校验脚本

### 阶段 B: 性能闭环 (W31 D4 - W32 D1)
- [ ] B1: docker-compose 部署 Prometheus + Grafana + node_exporter
- [ ] B2: wrk 压测脚本 (10w 并发, 5 个核心接口)
- [ ] B3: 慢查询分析 + APM (SkyWalking) + 优化报告

### 阶段 C: 客户体验 (W32 D2-D5)
- [ ] C1: 用户旅程地图可视化 + 首屏 <1.5s 实测
- [ ] C2: 4 渠道反馈 + 7 日闭环 SOP + NPS 调研

### 阶段 D: 质量管控 (W33 D1-D5)
- [ ] D1: JaCoCo 单测覆盖率 76% → 90%+
- [ ] D2: CODEOWNERS + 灰度白名单 + 阶梯发布
- [ ] D3: SonarQube + 缺陷率监控 + CI 集成

---

## 六、关键风险与决策

| 风险 | 等级 | 应对 |
|------|------|------|
| Prometheus + Grafana 部署需 docker | P2 | 复用 docker-compose.yml, 加 prometheus/grafana 服务 |
| 10w 并发需多机 | P2 | 单机可达 ~5k, 分布式压测需 5+ 节点 |
| 双人审核需团队配合 | P1 | 自我授权 + AI 审核 + 用户抽查 |
| 灰度需生产流量切分 | P1 | mc:feature flag + IP 白名单 (已部分支持) |

---

## 七、签发声明

本人作为项目总工程师, 全权负责本项目所有决策与执行落地. 本报告依据
项目宪法 AGENTS.md + 生产安全铁律 PRODUCTION_SAFETY.md + 用户第 16-20 次
授权, 系统性推进 4 大治理方向.

**承诺**: W31-W33 三周内将现状分从 **7.25 → 9.0+**, 期间任何 P0/P1 问题
按 P0 事故流程立即响应 (15 分钟内上报 + 24 小时内复盘).

签发时间: 2026-07-20
签发人: 总工程师