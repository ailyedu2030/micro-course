# 微课管理平台 · AI 开发入口

> 本文件由 Claude Code / opencode 启动时自动加载。
> 定义项目技能体系、开发节奏、质量门禁。

---

## 技能体系（启动时自动加载）

本项目使用 3 层技能架构。AI 在编码前必须加载对应技能：

| 技能 | 路径 | 触发 |
|------|------|------|
| **微课平台（宪法）** | `.claude/skills/microcourse/SKILL.md` | 项目中任何操作 |
| **微课平台-后端** | `.claude/skills/microcourse-backend/SKILL.md` | 编写 Java 代码 |
| **微课平台-前端** | `.claude/skills/microcourse-frontend/SKILL.md` | 编写 Vue/JS 代码 |
| **开发 Spec（必读）** | `docs/开发规划/phase5-10-spec.md` | 开发任何新功能前必须逐条对照 |

**加载要求**：
- 每次编码前，**必须**先读取对应的 SKILL.md
- **每次开发新功能前，必须先读取 docs/开发规划/phase5-10-spec.md 定位当前 Phase 的任务清单**
- SKILL.md 中的 contracts 和禁止项是**绝对基线**，不可偏差
- 字段/路径/响应格式与 contracts 冲突 = **阻塞合并**
- **每个 Agent 交付前必须对照 spec 中的验收标准逐项自检**

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

## 开发流程（强制）

### Step 1 · 读契约
按需读取 contracts（`references/` 下 6 份 + 前端 6 份）确认所有约束。

### Step 2 · 预检
```bash
bash .claude/skills/microcourse/scripts/precheck.sh
```
退出码 != 0 → 禁止写代码。

### Step 3 · 编码
使用 Agent Team 并行开发。用 coder 类型 agent。不用 Lombok、不用 @Autowired 字段注入、构造器注入。

### Step 4 · 交叉验证（强制，不可跳过）
**每次开发阶段完成后，立即启动 5 维交叉验证**：

| 维度 | 审查内容 | Agent |
|------|---------|-------|
| R1 代码质量+契约 | Lombok/@Autowired/分页/响应/@PreAuthorize/ErrorCode | reviewer |
| R2 DB 迁移 | 逐表逐字段 vs 数据字典.md | reviewer |
| R3 安全+配置 | pom.xml CVE / JWT / Redis key / application.yml | reviewer |
| R4 跨域一致性 | FK 链 / 命名 / REST 路径 / Service 接口 | reviewer |
| **R5 前端 UI/UX + 交互逻辑** | **见下方细分 3 路** | **3 reviewer 并行** |

#### R5 前端审查（必查，否则不可上线）

R5 拆为 3 个并行 sub-reviewer，覆盖前端结构性 bug：

| Sub | 焦点 | 必查项 |
|-----|------|--------|
| **R5a 视觉与一致性** | UI 组件规范、主题统一、三态齐全 | Element Plus 组件用法、Loading/Empty/Error 状态、响应式断点、a11y 基础 |
| **R5b 页面与功能交互** | 路由、表单、按钮、用户反馈 | 路由守卫、角色分离、表单提交、token 持久化路径、错误 toast 覆盖 |
| **R5c 数据交互** | Pinia / API / 缓存 / 分页 | store action/getter 一致性、API 调用时机、localStorage/sessionStorage 一致性、分页/列表数据流 |

**铁律**：
- 5 维 reviewer **必须并行**启动，不能串行
- 任一 FAIL → 立即修复 → 重新审查
- 全部 PASS → 才能 git commit

**输出规范**：每条审查结果必须标注 `P0 / P1-C / P1-I / P2`，不标注的审查报告视为无效。

### Step 5 · 提交
```
Commit message 格式: <type>(<scope>): <描述>
必须标注"交叉验证通过(R1-R5)"
```

---

## 缺陷分级标准

| 级别 | 定义 | 示例 |
|------|------|------|
| **P0** | 数据安全 / 核心功能不可用 / 客户首次操作必现错误 | 支付验证绕过、登录失败、页面白屏 |
| **P1-C** | **客户可感知**的不一致、错误消息、体验降级 | 显示错误信息、按钮失效、数据不一致 |
| **P1-I** | **内部仅见**的代码/文档问题，客户在正常使用中不可感知 | 数据字典未同步、命名规范、死代码、.bak 文件 |
| **P2** | 代码整洁、安全加固建议、性能优化 | chunk 体积、CSP 策略、日志级别 |

## 发布门禁（Release Gate）

| 门禁 | 要求 | 说明 |
|------|------|------|
| precheck.sh | 14/14 PASS | 违反 = 阻塞 |
| mvn compile | 0 ERROR | 违反 = 阻塞 |
| npm build | SUCCESS | 违反 = 阻塞 |
| E2E 测试 | 100% PASS | 违反 = 阻塞 |
| deploy-dryrun | 0 FAIL | 违反 = 阻塞 |
| **P0 缺陷** | **零容忍** | 发现 1 个即阻塞，全部修复 |
| **P1-C 缺陷** | **零容忍** | 发现 1 个即阻塞，全部修复 |
| P1-I 缺陷 | 可放行，但必须登记到 deferred-items.md | 总工程师逐条评估后签字 |
| P2 缺陷 | 可放行，记录即可 | 可批量归档到 backlog |

### 发布决策 5 步流程（不可跳过）

```
Step 1: 收集 R1-R5 审查报告
Step 2: 逐条过筛，标记每条为 P0 / P1-C / P1-I / P2
Step 3: P0 + P1-C → 全部修复 → 重新审查 → 进入下一步
Step 4: P1-I → 总工程师逐条评估 → 登记到 docs/deferred-items.md → 签字放行
Step 5: commit message 必须标注:
        "交叉验证通过(R1-R5) | P0+P1-C 已清零 | P1-I 登记 deferred-items.md"
```

### 铁律

- ❌ **禁止**：将 P1-C 误判为 P1-I 而放行（审查 → 总工程师逐条确认）
- ❌ **禁止**：批量放行 P1-I 而不逐个评估（每一条都要写清为什么可以 defer）
- ❌ **禁止**：deferred-items.md 中的条目超过 1 个版本不处理
- ✅ **每次发布前，deferred-items.md 必须是空的或仅有当前版本新增的条目**

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
