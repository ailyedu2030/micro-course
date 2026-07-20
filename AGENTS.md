# 微课管理平台 · AI 开发入口

> 本文件由 Claude Code / opencode **每次启动时自动加载**。
> 按场景按需加载 `docs/` 下的详细规范，见下方 **按需加载规则**。
> 详细开发流程 → `docs/开发流程-完整版.md`
> 发布管理规范 → `docs/发布管理.md`
> **生产安全铁律（必读）** → `docs/PRODUCTION_SAFETY.md`

---

## 🚨 P0 · 生产安全核心铁律 (5 行版)

**触发即 P0 事故：必须立即停止 + 报告用户 + 写事故复盘。**

1. **未通过本地 16/16 验证，禁止部署到生产**（`bash scripts/local-dev-deploy.sh` 必须 `✅ 16 通过`）
2. **禁止在生产服务器上调试**（禁止 `docker exec` 调试、`docker cp` 临时文件、ssh 做实验、Playwright 调试生产 URL）
3. **一次只做一件事**：同一容器 5 分钟内禁止重启/重建 > 1 次；第二次失败 → **必须停下来报告用户**，禁止第三次
4. **变更前必须备份**且 rollback 步骤写在 commit message 里
5. **生产 DB 写操作必须先 ask user**（引用 SQL 解释），禁止直连"修复脏数据"或"清理测试数据"

详细铁律 + 触发停手条件 + 违规识别清单 → `docs/PRODUCTION_SAFETY.md`

**违规后果**：任何 commit 含生产调试痕迹 = P0 事故 → 立即 revert + 24 小时内写 `docs/incidents/YYYY-MM-DD-<title>.md`

---

## 技能体系（启动时自动加载）

本项目使用 4 层技能架构。AI 在编码前必须加载对应技能：

| 技能 | 路径 | 触发 |
|------|------|------|
| **微课平台（宪法）** | `.claude/skills/microcourse/SKILL.md` | 项目中任何操作 |
| **微课平台-后端** | `.claude/skills/microcourse-backend/SKILL.md` | 编写 Java 代码 |
| **微课平台-前端** | `.claude/skills/microcourse-frontend/SKILL.md` | 编写 Vue/JS 代码 |
| **🚨 生产环境保护（铁律）** | `.claude/skills/production-safety/SKILL.md` | ssh/curl/playwright 操作前 |
| **开发 Spec（必读）** | `docs/开发规划/phase5-10-spec.md` | 开发任何新功能前必须逐条对照 |

**加载要求**：
- 每次编码前，**必须**先读取对应的 SKILL.md
- **每次开发新功能前，必须先读取 `docs/开发规划/phase5-10-spec.md` 定位当前 Phase**
- SKILL.md 中的 contracts 和禁止项是**绝对基线**，不可偏差
- 字段/路径/响应格式与 contracts 冲突 = **阻塞合并**
- **每个 Agent 交付前必须对照 spec 中的验收标准逐项自检**
- **🚨 每次 ssh/curl/playwright 操作前必须加载 production-safety skill,自检目标是否生产**

---

## Spec 强制对照机制

```yaml
Phase 开发工作流:
  Step 0: read docs/开发规划/phase5-10-spec.md → 定位当前 Phase
  Step 1: 提取该 Phase 的"功能/实现文件/API依赖/验收标准"四列表
  Step 2: 编码（严格按实现文件路径写入）
  Step 3: 自检 —— 对照 spec 逐项检查:
    □ 每个功能是否都有对应文件?
    □ 每个 API 依赖是否正确调用?
    □ 每项验收标准是否通过?
  Step 4: 交叉验证 —— 审查 Agent 必须对照 spec 审查
```

---

## 开发流程（索引）

**完整详细流程见 `docs/开发流程-完整版.md`**。每次开始编码/提交前必须先阅读该文件。

| 步骤 | 内容 | 关键规则 |
|------|------|---------|
| Step 1 | 读契约（references/ 下 contracts） | — |
| Step 2 | 预检（`bash precheck.sh`） | 退出码 != 0 → 禁止写代码 |
| Step 3 | 编码（Agent Team 并行） | 不用 Lombok / @Autowired / 构造器注入 |
| Step 4 | **5 维交叉验证（R1-R5，并行 reviewer）** | 任一 FAIL → 修复后重审 |
| Step 4.5 | 缺陷修复（**必须根因分析，禁止表面修复**） | 横向扫描 → 修复 → 重审 |
| Step 5 | **创建 PR**（分支命名 + Conventional Commit + Sign-off + Code Review） | 至少 1 人 Approved，禁止 self-approve |
| Step 6 | 合并与发布（squash merge + CHANGELOG + Feature Flag） | 禁止 merge commit，Breaking Change 必须标注 |

### Step 5.1 · Owner 自提 PR 的处理流程（强约束）

> **触发条件**: PR 作者 = 仓库 owner（`ailyedu2030`）时，必须按本流程图执行。
> **事故教训**: 2026-07-17 PR #30 走"临时降保护 → self-merge" 路径，违反纪律 5，详见 `docs/incidents/2026-07-17-PR30-merge-violation.md`。

```
Owner 自提 PR
    │
    ├─ 路径 A（推荐）: 联系任意有 write access 的同事网页 Approve
    │   └─ 1 名同事 Approve → gh pr merge --squash
    │
    ├─ 路径 B（次推荐）: Owner 关 PR → 同事用同事账号重开 PR + cherry-pick commits
    │   └─ 同事 PR 自动获得 Approve 资格 → 走正常流程
    │
    └─ 路径 C（最后手段，需用户明确接受合规风险）: 临时降保护 → merge → 恢复
        ├─ 前置: 用户明确说"单人开发 / solo / 就我自己"
        ├─ 前置: 必须先在 docs/incidents/YYYY-MM-DD-<title>.md 写事故复盘
        ├─ 执行: gh api PUT .../protection (enforce_admins=false, required_count=0)
        ├─ 执行: gh pr merge --squash
        └─ 强制: gh api PUT .../protection (enforce_admins=true, required_count=1)
                 （5 分钟内必须恢复，否则视为流程违规）
```

**AI 强制行为**:
- ❌ **AI 不能主动推荐路径 C** —— 必须先解释路径 A/B 的合规性
- ❌ **AI 不能跳过事故复盘就建议降保护** —— 必须先写复盘再操作
- ❌ **AI 不能在同一周内推荐 2 次路径 C** —— 需要升级到 PR owner 人工处理
- ✅ **AI 必须把 PR 状态、CI 状态、reviewer 状态透明展示给用户**
- ✅ **AI 在 PR #N+1 必须主动提示「上次的复盘是否落地了？」**

---

## 缺陷分级标准

| 级别 | 定义 | 示例 |
|------|------|------|
| **P0** | 数据安全 / 核心功能不可用 / 客户首次操作必现错误 | 支付验证绕过、登录失败、页面白屏 |
| **P1-C** | **客户可感知**的不一致、错误消息、体验降级 | 显示错误信息、按钮失效、数据不一致 |
| **P1-I** | **内部仅见**的代码/文档问题，客户正常使用中不可感知 | 数据字典未同步、命名规范、死代码 |
| **P2** | 代码整洁、安全加固建议、性能优化 | chunk 体积、CSP 策略、日志级别 |

---

## PR 分级审批规则（Step 5.1）

> **2026-07-20 决策 (D5)**: 针对 owner 自提 PR（当前项目 100% 为此类）建立分级审批，
> 解决 0 活跃 reviewer 导致的 PR 积压问题。**每周 owner 走路径 C（降级保护）≤ 1 次。**

### 分级规则

| PR 变更等级 | 审批要求 | 适用场景 | 示例 |
|------------|---------|---------|------|
| **🔴 P0 级** | **必须 2 人 approve**（含 owner 以外至少 1 人） | 安全漏洞、数据修复、DB schema 变更、认证授权 | IDOR 修复、V202 migration、payment 逻辑 |
| **🟡 P1-C 级** | **1 人 approve**（可含 owner） | 客户可感知 Bug 修复、功能回归、UI 错误 | teacherId 占位 Bug、路由错误、数据显示错误 |
| **🟢 P1-I / P2 / 文档** | **owner 1 人 approve + 24h 评论期**（AI 可 self-approve 若 owner 缺席 > 24h） | 内部文档、代码整洁、dependabot、协作方案 | 事故复盘、README、dependabot、retro |

### AI 行为约束

```yaml
路径 A (常规): PR 创建 → 等 reviewer approve → squash merge
  适用: 有活跃 reviewer 时的所有 PR

路径 B (分级自审): PR 创建 → 分级规则判定 → 满足条件则 self-approve → squash merge
  适用: P1-C/P1-I/P2/文档，且 24h 评论期内无异议

路径 C (降级保护): 临时降低 branch protection → self-merge → 恢复 protection
  适用: 🔴 仅 P0 紧急修复且 reviewer 不可用时
  约束:
    - 每周最多 1 次
    - 必须写 incident report（24h 内提交 docs/incidents/）
    - AI 禁止主动推荐路径 C
    - AI 禁止在同周内二次走路径 C
    - 路径 C 后必须复盘 + 提改进措施
```

### owner PR 处理流程

```
owner 推送 PR
  ↓
AI 自动分级（P0 / P1-C / P1-I / P2）
  ↓
┌─ P0 ─────────────────────────────────────────┐
│ 1. AI 禁止 self-approve                       │
│ 2. 等待 ≥ 1 位非 owner reviewer approve       │
│ 3. 若 reviewer 不可用 → 路径 C（严格限制）      │
└──────────────────────────────────────────────┘
┌─ P1-C ───────────────────────────────────────┐
│ 1. 优先等 reviewer approve                    │
│ 2. 24h 无人审 → AI 可 self-approve            │
└──────────────────────────────────────────────┘
┌─ P1-I / P2 / 文档 ───────────────────────────┐
│ 1. 创建 PR 后等待 24h 评论期                   │
│ 2. 24h 内无实质性异议 → self-approve + merge  │
│ 3. 若有异议 → 修改后重置 24h 计时器             │
└──────────────────────────────────────────────┘
```

### 合规检查清单（每次 owner PR 前）

- [ ] PR 描述标注了变更等级（P0 / P1-C / P1-I / P2）
- [ ] 若为 P0，确认至少 1 位 reviewer 可用；若不可用，确认本周路径 C 未使用
- [ ] 若为 P1-C，确认 24h 评论期已配置
- [ ] 若走路径 C，确认 incident report 模板已准备
- [ ] PR 标题含 Conventional Commit 前缀
- [ ] CI 5/5 全绿

关联：`docs/decisions/DECISION-2026-07-20.md` (D5)

---

## 按需加载规则

| 场景 | 必须读取 | 加载原因 |
|------|---------|---------|
| **开始编码 / 提交代码** | `docs/开发流程-完整版.md` | Step 3-6 完整规范（R5 审查、根因分析、PR 模板、Conventional Commit） |
| **发布决策 / 放行** | `docs/发布管理.md` | 发布门禁表、决策 6 步流程、总工程师放行纪律 7 条 |
| **ssh/curl/playwright 操作前** | `.claude/skills/production-safety/SKILL.md`（自动加载） | 生产环境保护铁律 |
| **开发新功能前** | `docs/开发规划/phase5-10-spec.md` | 定位当前 Phase 任务清单 |

---

## 🚨 生产安全快速检查

> **2026-07-01 事故教训**: DatePickerYM 修复时直接 ssh 到生产服务器改代码 → Playwright 在生产 URL 验证 → 创建了 proposal 41-53 真实脏数据。**绝不可重演**。
>
> 本章节是 P0 级硬约束，违反任一条 = 立即停止 + 报告用户。

**绝对禁止**：
- ❌ 在生产做实验（ssh 改代码 / Playwright 调试 / DB 插入测试数据）
- ❌ 跳过 Step 1-3 直接部署到生产
- ❌ 用生产用户名（xiaona）复刻到本地 DB
- ❌ 在生产 DB 做写操作而不 ask user
- ❌ 生产容器上做实验（micro-course-api-1 / micro-course-admin-1 / postgres / redis）
- ❌ 全量部署新功能（必须走灰度白名单）
- ❌ 在未通过生产门禁时操作生产（必须先 `bash scripts/deploy-gate.sh check`）

**必须**：
- ✅ audit trail：每次生产操作前 dump 当前状态，commit message 写明操作步骤
- ✅ rollback 路径：ROLLBACK_PLAN.md 覆盖最近 3 个版本
- ✅ 先通过生产门禁：`bash scripts/deploy-gate.sh check`

**自检**（每次 ssh/curl/playwright 前）：
```bash
# 生产标识: 100.74.122.13, microcourse.ailyedu.cn, frp 内网
# 如果是 → 必须确认完整部署流程已完成, 否则 exit 1
```
详见 `.claude/skills/production-safety/SKILL.md`（自动加载）。

---

## 技术栈

| 层 | 载体 |
|----|------|
| 后端 | Spring Boot 3.2.12 + Java 17 + MyBatis-Plus 3.5.6 + PostgreSQL 17.5 + Redis 7 |
| 前端 | Vue 3.4 + Element Plus 2.5 + Pinia 2.1 + Vite 5 + Axios 1.6 |
| 数据库 | PostgreSQL 17.5（本机 homebrew）+ Flyway 9.22.3 |
| 缓存 | Redis 7（Docker, localhost:6379） |

## 项目根路径
`/Users/jackie/微课平台`
