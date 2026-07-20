# 微课平台 · 用户体验治理体系 · 启动交付报告

> **报告期**：2026-07-21 (初始化落地)
> **签发**：项目总负责人 (UX 负责人)
> **版本**：v1 启动基线
> **合并 PR**：[#51](https://github.com/ailyedu2030/micro-course/pull/51)
> **关联 commit**：`06e90660` (squash-merged to main)

---

## 1. 一句话总结

> **自 2026-07-21 起，微课平台进入"双轴治理"模式：研发轴 (5 套既有) + 体验轴 (本次启动的 7 套治理动作)。**
> 17 个文件落地，CI 7/7 全绿，Bot auto-approve，无生产侵入。

---

## 2. 治理架构升级

### 2.1 既有的 5 套治理 (研发轴 - Code Axis)
1. AGENTS.md (P0 生产安全铁律)
2. docs/开发流程-完整版.md
3. docs/发布管理.md
4. docs/PRODUCTION_SAFETY.md
5. monitoring/ (Prometheus + AlertManager + Grafana)
6. .github/workflows/auto-approve.yml (CI 门禁)

### 2.2 本次启动的 7 套动作 (体验轴 - User Experience Axis)

| # | 动作 | 节奏 | 主文档 |
|---|------|------|--------|
| 1 | 全链路体验量化标准 | 持续度量 | [01 量化标准](./ux-governance/01-metrics-standard.md) |
| 2 | 每周 UX 专项复盘会 | 每周 1 次 | [02 周复盘](./ux-governance/02-weekly-review-ritual.md) |
| 3 | 核心功能体验设计评审 | 每个新功能上板前 | [03 反馈 SLA](./ux-governance/03-feedback-sla.md) |
| 4 | 用户反馈快速响应通道 | 24h / 72h | [03 反馈 SLA](./ux-governance/03-feedback-sla.md) |
| 5 | 灰度调研 / 实验室可用性 / 全场景压测 | 每季度 | [07 研究 SOP](./ux-governance/07-research-lab-sop.md) |
| 6 | 1h 排查 / 4h 修复应急机制 | 实时 | [05 应急响应](./ux-governance/05-incident-response.md) |
| 7 | 项目体验健康度月报 | 月度 + 季度 | [06 健康度报告](./ux-governance/06-health-report-template.md) |

横向贯穿：[04 无障碍基线](./ux-governance/04-accessibility-baseline.md)

---

## 3. PR #51 落地清单

### 3.1 文件统计
- **17 个文件** 全部新增 / 修改
- **0 个** Java / Vue / SQL 代码改动（避免污染生产）
- **0 个** 后端 / 迁移 / 配置文件变化

### 3.2 文件清单
```
docs/governance/ux-governance/
├── README.md                              [新] 总索引 + 角色 + Roadmap
├── 01-metrics-standard.md                 [新] 5 维度阈值表
├── 02-weekly-review-ritual.md             [新] 周复盘机制
├── 03-feedback-sla.md                     [新] 反馈 SLA + 工单分级
├── 04-accessibility-baseline.md           [新] a11y 基线
├── 05-incident-response.md                [新] 1h/4h 应急机制
├── 06-health-report-template.md           [新] 月度健康度报告模板
├── 07-research-lab-sop.md                 [新] 研究 SOP
├── feedback-pool.md                       [新] 反馈池 (初始 empty)
├── rostering.md                           [新] 6 角色值班表
├── announcements/2026-07-21-ux-governance-launch.md  [新] 启动公告
├── templates/weekly-review-minutes.md     [新] 复盘会纪要模板
├── templates/feedback-ticket.md           [新] 反馈工单模板
├── templates/a11y-acceptance-checklist.md [新] PR 必填清单
├── templates/incident-postmortem.md       [新] 事故 5 Whys 模板
├── templates/health-report-monthly.md     [新] 月报骨架模板
├── minutes/  incidents/  incidents/closed/  research/  research/elderly/
│   health-reports/  raw/                  [新] 7 个运行时子目录 (空)
└── AGENTS.md                              [修改] 按需加载规则 +2 行
```

### 3.3 CI 验证
| Job | 状态 | 耗时 |
|-----|------|------|
| backend | ✅ pass | 3m58s |
| frontend | ✅ pass | 45s |
| docker | ✅ pass | 2m44s |
| e2e | ✅ pass | 3s |
| monitoring-lint | ✅ pass | 15s |
| Trivy Scan | ✅ pass | 35s |
| auto-approve (Bot) | ✅ pass + APPROVED | 7m6s |

**7/7 全绿；mergeStateStatus: CLEAN**

---

## 4. 治理机制详解

### 4.1 量化标准维度 (5 维)

```
维度 A  加载性能       ≤1.5s FCP / ≤2.5s LCP / ≤3.5s TTI
维度 B  交互流畅度     ≤200ms INP / ≤800ms API p95
维度 C  易用性         ≥95% 主任务完成率 / ≤3 次点击
维度 D  视觉舒适度     WCAG AA 100% / 老年字号 ≥18px
维度 E  故障恢复       ≤5min 致命恢复 / ≥90% 用户回访
```

### 4.2 反馈 SLA 分级 (5 级)

| 等级 | 首响应 | 方案 | 修复 | 回访 |
|------|--------|------|------|------|
| P0-C | 1h | 24h | 4h (应急) | 1d |
| P1-C | 4h | 72h | 5d | 3d |
| P1-R | 4h | 72h | 10d | 5d |
| P2-R | 24h | 5d | 30d | 7d |
| P3-R | 24h | 14d | 下个 sprint | 14d |

> 区分 P1-C (可见错误) 与 P1-R (流失原因)。R 是更隐蔽的流失，需重点跟踪。

### 4.3 无障碍基线 (WCAG 2.1 AA + 老年)

- **POUR 四原则强制**：13 个 WCAG AA 强制项
- **老年用户专项**：≥18px 字号 / ≥56×56 点击 / 客服电话固定可见
- **残障专项**：视障 (NVDA) / 听障 (字幕) / 运动障碍 (键盘) / 读写障碍 / 认知
- **PR 必填清单**：[a11y-acceptance-checklist.md](./ux-governance/templates/a11y-acceptance-checklist.md)

### 4.4 应急响应剧本 (4 个场景)

1. 大面积白屏 / API 5xx
2. 富文本编辑器异常（Quill 失败）
3. a11y 紧急失效（视觉断裂）
4. 老年用户外呼集中投诉

每个剧本给出 **T+0 / T+1h / T+4h / T+24h** 时间线，IC = 项目总负责人。

---

## 5. 启动即生效的 4 条红线

1. **任何 PR 不带 a11y 验收清单 → 不合并**
2. **任何用户反馈未录入 feedback-pool.md → 反馈 SLA 失效**
3. **任何 UX-P0 事故 24h 内无 Postmortem → 治理失守**
4. **任何月度健康度报告延期 ≥7 天 → 不允许签发**

> 这 4 条已在 [announcements/2026-07-21-ux-governance-launch.md](./ux-governance/announcements/2026-07-21-ux-governance-launch.md) 中签发声明。

---

## 6. 与既有治理的接口

| 接口 | 谁负责 | 何时启用 |
|------|-------|---------|
| AGENTS.md P0 铁律 ↔ 应急响应 05 | 项目总负责人 | 立即 |
| 分支保护 ↔ a11y PR 清单 | CI 门禁 | 下个 PR 起强制 |
| monitoring-lint ↔ UX 5 维度 | 运维值班 + 前端值班 | 一周内 |
| PRODUCTION_SAFETY ↔ 应急回滚 | 运维值班 | 立即 |
| AGENTS.md 按需加载 ↔ UX 治理章节 | AI 启动时 | 已生效 |

---

## 7. 后续 Roadmap (5 项 · 启动后 90 天内)

- [ ] **2 周内**：第一节线上 UX 复盘会（覆盖启动周的反馈）
- [ ] **2026-08-22 前**：第一份月度体验健康度报告（覆盖 2026-07-22 ~ 2026-08-22）
- [ ] **2026-Q3 内**：第一轮老年用户外呼调研（≥10 位）
- [ ] **持续**：WCAG 2.1 AA 全量扫描基线
- [ ] **2 周内**：健康度数字看板接入 Grafana

> 不在本次 PR。需后续 PR 单独推进。

---

## 8. 已知未做事项（透明披露）

1. **排班表未填**：rostering.md 第 N 周期是空的，需项目总负责人在启动 7 天内补齐
2. **反馈池为空**：feedback-pool.md 是占位文件，首次反馈前需由产品值班维护
3. **录像/SOP 工具未采购**：可用性实验室 SOP 提到 OBS/Vysor 等工具，需在 30 天内确认
4. **健康度看板未接入 Grafana**：本报告的 5 维度数据需先有 RUM 前端埋点
5. **NVDA 走查未启动**：残障专项研究需在 Q3 内做第 1 次

> **这些"未做"是有意的**：治理机制先于数据采集落地，确保后续工作有规范可循。

---

## 9. 治理演化原则

- **数据说话**：阈值调整必须有 ≥2 周数据佐证
- **只改不删**：淘汰指标保留在历史阈值小节
- **永不追责个人**：Postmortem 严禁追责个人
- **禁止 1 个人顶 3 个角色**：值班必须 1 对 1
- **可中断 ≠ 永久停**：治理动作可暂停（如临时排班冲突），但必须重启 + 写明原因

---

## 10. 签字

- 起草：项目总负责人 (UX 负责人)
- 已合并 PR：#51 → main (commit `06e90660`)
- 关联 W37 PR：#50 (Quill + 压测 + Phase 5)
- 关联 W36 PR：#49 (课件架构 8 项需求)

> **本报告与[启动公告](./ux-governance/announcements/2026-07-21-ux-governance-launch.md)同日签发，长期有效。**

签发时间：2026-07-21 23:30 (UTC+8)
