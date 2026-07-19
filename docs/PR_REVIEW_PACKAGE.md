# Reviewer 请求包 · 2026-07-18

> 写给团队成员，请求 review 三个 owner 自提 PR + 简评 12 个待审 PR。

---

## 🎯 三个紧急 PR（owner 自提，需要 1 人 approve）

### PR #31 — 文档（无风险）
- **内容**：事故复盘 + ROLLBACK_PLAN v1.22.0 + staging checklist + AGENTS.md Step 5.1
- **影响**：纯文档，0 业务影响
- **风险评估**：🟢 极低
- **建议 review 时间**：10 分钟
- **关键看点**：AGENTS.md 第 79 行后新增的 Step 5.1 owner PR 流程图

🔗 https://github.com/ailyedu2030/micro-course/pull/31

### PR #32 — Round 2-1: teacherId 占位修复
- **内容**：V202 schema 迁移（teacher_id 允许 NULL） + 后端校验 + 前端不写 teacherId + 4 测试
- **影响**：含 DB schema 变更，需要 staging 先验证
- **风险评估**：🟡 中等（schema 变更 + 历史脏数据自动修正）
- **建议 review 时间**：30 分钟
- **关键看点**：
  - V202 schema 变更（V202__chapter_teacher_teacher_id_nullable.sql）
  - 后端 P0-2 校验（StorageApplicationCudServiceImpl 第 320-340 行）
  - 前端 toggleChapter 不再写 teacherId（MicroSpecialtyProposal.vue 第 598-620 行）

🔗 https://github.com/ailyedu2030/micro-course/pull/32

### PR #33 — Round 2-2: SectionController IDOR 修复
- **内容**：SectionServiceImpl 读路径加 assertOwner + SectionSlide 加 ownership 校验 + 3 测试
- **影响**：应用层修复，无 schema 变更
- **风险评估**：🟢 低（纯权限收紧）
- **建议 review 时间**：20 分钟
- **关键看点**：
  - SectionServiceImpl.listByChapter / getById 加 assertOwner
  - SectionSlideController.getSectionSlide 新增 ownership 校验（49-95 行）

🔗 https://github.com/ailyedu2030/micro-course/pull/33

---

## 📋 12 个其它待审 PR（建议按风险优先级）

### 高优先级（功能影响）

#### PR #15 — P0~P2 165 项审计修复
- **风险评估**：🔴 高（大规模批量修复，建议分批合并）
- **建议**：先看 165 项清单，确认是否含 schema 变更、是否含权限收紧
- **行动**：reviewer 至少需要 2-3 小时；建议拆分为 3-4 个 PR

#### PR #28 — 音频上传与 TTS 生成
- **风险评估**：🟡 中（含 teacher/admin 端点，需检查 ownership）
- **建议**：review 重点是 ownership 校验是否齐全（看 AudioUploadController / NarrationController / AdminTtsController）
- **建议 review 时间**：30 分钟

#### PR #22 — E2E 替换硬编码 sleep
- **风险评估**：🟢 低（CI 改进）
- **建议**：与 PR #31 配套合入

### 中优先级（依赖升级）

#### PR #5/6/7/8/9 — Java 依赖升级（5 个）
- mybatis-plus-spring-boot3-starter: 3.5.6 → 3.5.16
- jaxb-runtime: 2.3.8 → 4.0.9（major）
- jsoup: 1.18.3 → 1.22.2
- jjwt: 0.12.6 → 0.13.0（minor）
- poi-ooxml-full: 5.3.0 → 5.5.1
- **风险评估**：🟡 中（jaxb 是 major 升级，可能需要 API 调整）

#### PR #10/11 — 前端 dev 依赖升级
- eslint: 10.4.1 → 10.7.0
- @playwright/test: 1.61.0 → 1.61.1

### 低优先级（一般升级）

#### PR #12/13/14 — 前端运行时依赖升级
- vue-echarts: 6.7.3 → 8.0.1（major）
- vue-router: 4.6.4 → 5.1.0（major）
- element-plus: 2.14.1 → 2.14.3

---

## 🛡️ 合并顺序建议（项目负责人视角）

1. **第一批（今日）**：PR #31 + #33 + #22（无 schema 变更，最安全）
2. **第二批（staging 验证后）**：PR #32（V202 schema 变更）
3. **第三批（评估后）**：PR #28（音频上传）
4. **第四批（拆分后）**：PR #15（165 项审计）
5. **第五批（QA 测试后）**：依赖升级（PR #5-14）

---

## 📊 当前审查积压指标

| 指标 | 数值 |
|------|------|
| 开放 PR 总数 | 15 |
| owner 自提 PR | 3 (#31, #32, #33) |
| 依赖升级 PR | 11 (#5-14, #22) |
| 大批量修复 PR | 1 (#15) |
| 新功能 PR | 1 (#28) |
| 平均等待 approve 时间 | > 24 小时 |
| Reviewer 数 | 0 人活跃 |

---

## ⚠️ 风险警告

按 AGENTS.md Step 5.1：
- **本周已走过 1 次 owner self-merge**（PR #30 通过降保护路径）
- **本周不能再走路径 C**——所有 PR 必须等 reviewer approve
- **未通过 staging 验证禁止部署生产**（PR #32 是 schema 变更，必须 staging 先验证）

---

## 🤝 求助

如果团队内**无活跃 reviewer**，建议：
1. 邀请 1-2 名同事加入 collaborator
2. 或暂时关掉 batch 流程到下周处理
3. 或考虑第三方代码 review 工具（GitHub Copilot Review / SonarQube）

---

**文档版本**: 1.0
**创建时间**: 2026-07-18
**关联 PR**: #31, #32, #33
**下一步**: 通知团队 reviewer + 等 approve