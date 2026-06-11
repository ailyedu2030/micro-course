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
**每次开发阶段完成后，立即启动 4 维交叉验证**：

| 维度 | 审查内容 | Agent |
|------|---------|-------|
| R1 代码质量+契约 | Lombok/@Autowired/分页/响应/@PreAuthorize/ErrorCode | reviewer |
| R2 DB 迁移 | 逐表逐字段 vs 数据字典.md | reviewer |
| R3 安全+配置 | pom.xml CVE / JWT / Redis key / application.yml | reviewer |
| R4 跨域一致性 | FK 链 / 命名 / REST 路径 / Service 接口 | reviewer |

**铁律**：
- 4 个 reviewer **必须并行**启动，不能串行
- 任一 FAIL → 立即修复 → 重新审查
- 全部 PASS → 才能 git commit

### Step 5 · 提交
```
Commit message 格式: <type>(<scope>): <描述>
必须标注"交叉验证通过(R1-R4)"
```

---

## 质量门禁

| 门禁 | 要求 |
|------|------|
| precheck.sh | 13/13 PASS |
| mvn compile | 0 ERROR |
| npm build | SUCCESS |
| 交叉验证 | R1-R4 全部 PASS，0 P0 阻塞 |

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
