# 微课平台 · 用户体验治理体系 (UX Governance)

> **项目生命周期治理 · 第 7 套机制**
> 自 2026-07-21 起，作为项目总负责人在 `ailyedu2030/micro-course` 持续推进。
> 一旦落定，**不可中断**，与既有的研发/CI/监控/发布治理一起，构成项目全生命周期运营的 7 套机制。

---

## 治理目标

微课平台面对 K12 / 职教 / 语言 / 老年大学四类目标客群，**用户体验不是"附加项"**，而是与功能、性能、可用性并列的 P0 治理对象。本体系围绕**用户视角**把 7 项治理动作制度化：

| # | 治理动作 | 节奏 | 主文档 |
|---|---------|------|--------|
| 1 | 每周用户体验专项复盘 | 每周固定时间 | [02 每周体验复盘会议](./02-weekly-review-ritual.md) |
| 2 | 全链路体验量化标准 | 持续度量 | [01 量化标准 v1](./01-metrics-standard.md) |
| 3 | 核心功能体验设计评审 | 每个新功能上板前 | [03 反馈通道 + 分级](./03-feedback-sla.md) |
| 4 | 用户反馈快速响应通道 | 24h/72h | [03 反馈通道 + 分级](./03-feedback-sla.md) |
| 5 | 灰度调研 / 实验室可用性测试 / 全场景压测 | 每季度 | [07 研究实验室 SOP](./07-research-lab-sop.md) |
| 6 | 1h 排查 / 4h 修复或灰度应急机制 | 实时 | [05 应急响应机制](./05-incident-response.md) |
| 7 | 项目体验健康度报告 | 月度 + 季度 | [06 健康度报告模板](./06-health-report-template.md) |

并以 [04 无障碍基线](./04-accessibility-baseline.md) 横向贯穿 1-7 所有动作。

## ⛳ 宪法层 (L0 · UX 至上铁律)

2026-07-21 启动即生效的本体系**只令层 (4 条不可妥协)** 与**用户 5 条要求落地执行框架**:

- [EXECUTIVE-PRINCIPLE.md](./EXECUTIVE-PRINCIPLE.md) — UX 至上铁律宪法 (4 只令 + 5 点落地 + 与既有治理集成)
- [L0-MANIFESTO.md](./L0-MANIFESTO.md) — UX 攻坚 1 号动员令 (宣告 + R6 6 角色团队 + 6 触点管理 + 启动资源)
- 已嵌入 AGENTS.md (L0 段高于 P0, 仅次于监管不可抗力)
- 已嵌入 [.github/PULL_REQUEST_TEMPLATE.md](../../../.github/PULL_REQUEST_TEMPLATE.md) (UX 评审 owner 强制 + 12 项 a11y checklist + 6 触点评审门禁)
- 已嵌入 [rostering.md](./rostering.md) R1-R5+ 资源保障 + R5++ 跨部门仲裁 + R5+++ 启动资金
- 已嵌入 [02-weekly-review-ritual.md § 7 一票否决清单](./02-weekly-review-ritual.md)
- 已嵌入 [07-research-lab-sop.md § 10 多维度 4 法研究](./07-research-lab-sop.md)
- 已嵌入 [01-metrics-standard.md § 7 10 项核心度量 + 阈值红线](./01-metrics-standard.md)

> **总负责人签发**: UX 铁律在所有治理冲突中默认胜出. 任何"为赶工违反 UX 决策"按 P0-above 处理.

---

## 关键角色与职责

> 微课项目治理的特殊性：**没有专职 UX 团队**。治理动作由总负责人兼任召集人，由跨职能角色轮值共担。

| 角色 | 职责范围 | 在 UX 治理中的位置 |
|------|---------|------------------|
| **项目总负责人（UX 负责人）** | 统筹 7 项治理动作 / 签发体验健康度报告 / 召集复盘会 / 主持灰度调研 | 本人 |
| **后端值班** | API 性能 / 错误率 / 缓存 / 限流 | 性能指标 owner |
| **前端值班** | FCP / TTI / 交互流畅度 / a11y bug | 交互 & a11y owner |
| **测试值班** | e2e / 可用性测试 / bug 跟踪 | 体验门禁 owner |
| **运维值班** | 监控告警 / 灰度开关 / 回滚 | 应急响应 owner |
| **产品值班** | 用户故事 / 反馈通道值守 / 投诉闭环 | 反馈 SLA owner |
| **客服/外呼轮值** | 接待老年用户电话 / 记录问题 | 无障碍 + 老年场景 owner |

> 角色排班表：`docs/governance/ux-governance/rostering.md`（运行第一周补齐）

---

## 与既有治理机制的关系

```
已有治理（研发侧）
├─ AGENTS.md (P0 生产安全铁律 / 开发流程)
├─ docs/开发流程-完整版.md
├─ docs/发布管理.md
├─ docs/PRODUCTION_SAFETY.md
└─ monitoring/ (Prometheus + AlertManager + Grafana)

新增治理（用户体验侧 — 本目录）
├─ 指标定义 (01)
├─ 复盘节奏 (02)
├─ 反馈链路 (03)
├─ 无障碍基线 (04)
├─ 应急剧本 (05)
├─ 健康度报告 (06)
└─ 研究 SOP (07)

二者关系：
- 体验指标 = 业务侧"健康度"信号源
- 监控告警 = 技术侧"运行态"信号源
- 健康度报告 = 把业务+技术两侧信号聚合输出给"用户"的语言
```

---

## 开始阅读

| 你想看 | 阅读顺序 |
|--------|---------|
| 治理哲学 & 量化标准 | [01 量化标准](./01-metrics-standard.md) |
| 想加入每周复盘 | [02 每周体验复盘](./02-weekly-review-ritual.md) + [templates/weekly-review-minutes.md](./templates/weekly-review-minutes.md) |
| 反馈入口在哪 / SLA 多少 | [03 反馈通道 + 分级](./03-feedback-sla.md) + [templates/feedback-ticket.md](./templates/feedback-ticket.md) |
| 上线前 a11y 怎么查 | [04 无障碍基线](./04-accessibility-baseline.md) + [templates/a11y-acceptance-checklist.md](./templates/a11y-acceptance-checklist.md) |
| 出问题了怎么响应 | [05 应急响应机制](./05-incident-response.md) |
| 看月度体验健康度 | [06 健康度报告模板](./06-health-report-template.md) + [templates/health-report-monthly.md](./templates/health-report-monthly.md) |
| 实验室可用性测试怎么做 | [07 研究实验室 SOP](./07-research-lab-sop.md) |

---

## 后续 Roadmap

- [ ] 第一节**线上用户体验月度复盘会**（时间待定）
- [ ] 第一份**月度体验健康度报告**（覆盖 2026-07-22 ~ 2026-08-22）
- [ ] 第一轮**老年用户外呼调研**（覆盖 ≥10 位真实老年用户）
- [ ] **WCAG 2.1 AA 全量扫描基线**
- [ ] **健康度数字看板**接入 Grafana

> 任何角色参与这些动作前，**优先读自己负责章节 + 关联模板**。
> 不需要"开发流程"那种 5 步审计，但需要"批判性复盘 + 数据说话"两条纪律。
