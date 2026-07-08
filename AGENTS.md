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

---

## 缺陷分级标准

| 级别 | 定义 | 示例 |
|------|------|------|
| **P0** | 数据安全 / 核心功能不可用 / 客户首次操作必现错误 | 支付验证绕过、登录失败、页面白屏 |
| **P1-C** | **客户可感知**的不一致、错误消息、体验降级 | 显示错误信息、按钮失效、数据不一致 |
| **P1-I** | **内部仅见**的代码/文档问题，客户正常使用中不可感知 | 数据字典未同步、命名规范、死代码 |
| **P2** | 代码整洁、安全加固建议、性能优化 | chunk 体积、CSP 策略、日志级别 |

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
