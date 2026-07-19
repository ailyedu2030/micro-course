# 微课平台 · Reviewer 招募

> **状态**: 🔴 紧急 — 当前 0 活跃 reviewer，8 个 PR 等待 review
>
> **联系**: ailyedu2030@gmail.com 或 GitHub Issue

---

## 🙏 邀请

我们是 **ailyedu2030** —— 一个面向企业培训的开源微课管理平台（Spring Boot 3 + Vue 3 + PostgreSQL + Redis）。

目前仓库 [ailyedu2030/micro-course](https://github.com/ailyedu2030/micro-course) 有 8 个开放 PR 等待 review，其中包含：
- ✅ 完整审计修复（v1.22.0 已合入 main）
- 🔧 业务安全修复（IDOR 漏洞、teacherId 占位 bug）
- 📋 流程改进文档（事故复盘 + 协作改进方案）

我们邀请有经验的开发者加入 reviewer 池，每周花费 1-2 小时做 code review。

---

## 📋 Reviewer 工作量

| 项 | 数值 |
|----|------|
| 每周 PR 数量 | 1-3 个（改进后） |
| 每个 PR review 时间 | 15-30 分钟 |
| 总周时间 | 1-2 小时 |
| 紧急 PR（P0/P1-C） | 4 小时响应 |

---

## 🎯 Reviewer 需要的能力

### 必须（任一）
- Java 17 + Spring Boot 3 企业开发经验（>= 2 年）
- Vue 3 + TypeScript 现代前端经验（>= 2 年）
- PostgreSQL 数据库设计与优化经验

### 加分
- 教育/培训行业 SaaS 经验
- 多租户/支付/权限系统设计经验
- 中英文双语

---

## 📞 如何加入

### 路径 A（推荐）：联系 owner

1. 发送邮件到 ailyedu2030@gmail.com
2. 标题：`[Reviewer] <Your GitHub username> - <your expertise>`
3. 内容：
   - 你的 GitHub 用户名
   - 你的技术背景（200 字以内）
   - 每周可投入 review 时间
   - 时区

### 路径 B：直接发 PR

1. Fork 仓库
2. 修一个 [good first issue](https://github.com/ailyedu2030/micro-course/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22)
3. 通过 PR review 流程熟悉代码风格
4. 持续贡献 2-4 周后申请 reviewer 权限

---

## 🛡️ Reviewer 权利与责任

### 权利
- 仓库 read + write access
- 在 PR 上 approve / request changes / comment
- 在 issue tracker 上 assign 任务
- 参与月度 reviewer 会议

### 责任
- 按 SLA 响应 PR review 请求
- 不发布未经 PR review 的代码
- 维护代码风格与项目规范（AGENTS.md）
- 持续 4 周活跃 review 后，自动成为 core reviewer

---

## 📊 当前活跃 PR（等待 review）

| PR # | 标题 | 优先级 | 风险 |
|------|------|--------|------|
| #31 | 事故复盘 + AGENTS.md Step 5.1 | 🟢 文档 | 极低 |
| #32 | Round 2-1: teacherId 占位 (V202 schema) | 🟡 中 | 中等（schema 变更） |
| #33 | Round 2-2: SectionController IDOR | 🟢 修复 | 低 |
| #34 | Reviewer request package | 🟢 文档 | 极低 |
| #35 | PR #15 冲突风险评估 | 🟢 文档 | 极低 |
| #36 | 协作改进方案 | 🟢 文档 | 极低 |

---

## 🎁 Reviewer 福利

- 🌟 GitHub 个人页面 Pro 徽章
- 💼 LinkedIn 项目推荐
- 📚 年度技术峰会门票赞助（待定）
- ☕ 每月 1 次团队聚餐（线上/线下，远程地区发星巴克电子卡）

---

## 🌍 地区分布

我们欢迎全球 reviewer 加入。当前时区：
- 北京时间 (CST): 周一 ~ 周五 09:00-18:00
- 美东时间 (EST): 周二 ~ 周六 21:00-06:00
- 欧洲时间 (CET): 周二 ~ 周六 02:00-11:00

建议每个时区至少 1-2 名 reviewer，保证 24h 响应。

---

## 📜 历史背景

- **2026-07-17**: PR #30 合入（owner self-merge 路径，违反纪律 5）
- **2026-07-18**: 写事故复盘 + 关闭 10 dependabot 积压 PR
- **2026-07-18**: Round 2-1/2-2 完成（含 V202 schema 变更）
- **2026-07-18**: 发现 PR #15 与 PR #30 直接冲突（已加 review comment）
- **2026-07-18**: 发起 reviewer 招募（本文档）

完整事故复盘: docs/incidents/2026-07-17-PR30-merge-violation.md

---

## 🔗 快速链接

- 仓库: https://github.com/ailyedu2030/micro-course
- 开放 PR: https://github.com/ailyedu2030/micro-course/pulls
- 协作改进方案: docs/COLLABORATION_PLAN_2026-07-18.md
- AGENTS.md (项目 AI 入口): AGENTS.md

---

**联系方式**: ailyedu2030@gmail.com
**创建时间**: 2026-07-18
**下次更新**: 招募到第 1 位 reviewer 后