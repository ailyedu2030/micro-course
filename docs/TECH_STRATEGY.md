# 微课平台 · 技术战略规划

> **版本**: v1.0
> **编制**: AI 总工程师 (Claude)
> **批准**: ailyedu2030 (项目总负责人)
> **日期**: 2026-07-21
> **覆盖周期**: 2026-Q3 (7月-9月)
> **关联**: AGENTS.md / DECISION-2026-07-20.md / PRODUCTION_SAFETY.md

---

## 目录

1. [项目现状与基线](#一项目现状与基线)
2. [核心技术选型与架构方案](#二核心技术选型与架构方案)
3. [里程碑与阶段性交付节点](#三里程碑与阶段性交付节点)
4. [资源分配与责任边界](#四资源分配与责任边界)
5. [代码评审与技术审计标准](#五代码评审与技术审计标准)
6. [全流程质量管控体系](#六全流程质量管控体系)
7. [风险预警与应急响应机制](#七风险预警与应急响应机制)
8. [Stakeholder 沟通与流程优化](#八stakeholder-沟通与流程优化)
9. [技术资产沉淀与最佳实践](#九技术资产沉淀与最佳实践)

---

## 一、项目现状与基线

### 1.1 当前版本

| 指标 | 值 |
|------|-----|
| 生产版本 | v1.22.1 (灰度已发布) |
| main HEAD | `b6124ad7` (2026-07-21) |
| 合并 PR 数 | 41 |
| 活跃 PR 数 | 10 |
| 测试用例数 | 601 |
| CI 任务数 | 5 (backend / frontend / e2e / docker / trivy) |

### 1.2 技术栈基线

| 层 | 技术选型 | 版本 |
|----|---------|------|
| 后端框架 | Spring Boot | 3.2.12 |
| 语言 | Java | 17 |
| ORM | MyBatis-Plus | 3.5.6 |
| 数据库 | PostgreSQL | 17.5 |
| 缓存 | Redis | 7 (Docker) |
| 迁移工具 | Flyway | 9.22.3 |
| 前端框架 | Vue | 3.4 |
| UI 库 | Element Plus | 2.5 |
| 状态管理 | Pinia | 2.1 |
| 构建工具 | Vite | 5 |
| HTTP 客户端 | Axios | 1.6 |
| CI/CD | GitHub Actions | — |
| 容器化 | Docker Compose | — |

### 1.3 已完成的核心修复 (Round 1 + Round 2)

| PR | 修复内容 | 等级 | 状态 |
|----|---------|------|------|
| #30 | 套餐发课修复 + 微专业报名权限 + 退课级联 + 进度聚合 + 统计口径 | P0/P1-C | ✅ v1.22.0 |
| #32 | teacherId 占位符 Bug + V202 schema | P1-C | ✅ 7-21 |
| #33 | SectionController IDOR + SectionSlide ownership | P0 | ✅ 7-21 |
| #37 | 音频上传 metadata 保留 | P1-C | ✅ v1.22.1 |
| #38 | 课件架构重构 (125 files) | P1-C | 🔄 进行中 |

### 1.4 已知技术债

| 债项 | 严重度 | 估计工作量 |
|------|--------|-----------|
| PR #15 165 项审计修复未合入 | P0 (冲突风险) | 3-5 天 |
| 管理端 6 模块未审计 | P1-C (潜在漏洞) | 5-7 天 |
| Quiz 跨用户访问未验证 | P1-C | 2-3 天 |
| E2E 测试未运行 (已注释) | P1-I | 2-3 天 |
| staging 环境无自动化部署 | P1-I | 1-2 天 |
| 0 活跃 reviewer | P1-I (组织) | 长期 |

---

## 二、核心技术选型与架构方案

### 2.1 架构决策：单体优先，模块化演进

```
┌─────────────────────────────────────────────┐
│                  Nginx (frp)                 │
├──────────────────┬──────────────────────────┤
│  micro-course-   │  micro-course-            │
│  admin (Vue)     │  api (Spring Boot)        │
│  :3000           │  :8080                    │
├──────────────────┴──────────────────────────┤
│           PostgreSQL 17.5 :5432              │
│           Redis 7 :6379                      │
└─────────────────────────────────────────────┘
```

**决策理由**：
- 当前团队规模 1 人 + AI，微服务会显著增加运维负担和故障排查时间
- 单体应用可通过包结构模块化实现清晰的职责分离
- 未来若业务增长需要拆分，当前按 `com.microcourse.*` 包结构已天然形成模块边界

### 2.2 模块划分

```
micro-course-api/
├── controller/          # REST API 层
├── service/             # 业务逻辑层
│   └── impl/            # 实现类
├── repository/          # 数据访问层 (MyBatis-Plus)
├── entity/              # 数据实体
├── dto/                 # 数据传输对象
├── config/              # Spring 配置
├── util/                # 工具类
├── exception/           # 异常定义
└── plugin/
    └── interactive/     # 互动课件插件
        ├── controller/  # SlideController
        ├── service/     # SlideService
        ├── mapper/      # MyBatis Mapper
        └── dto/         # 插件 DTO
```

### 2.3 技术选型原则

| 原则 | 说明 |
|------|------|
| **稳定优先** | 不使用 alpha/beta 版本，依赖升级走 dependabot batch 模式 |
| **少依赖** | 不用 Lombok、@Autowired，避免"魔法"代码 |
| **构造器注入** | 所有依赖通过构造器注入，便于测试 |
| **无状态** | Service 层无状态，线程安全 |
| **显式优于隐式** | SQL 写在 Mapper XML 或 LambdaQueryWrapper，不用动态代理生成 |

### 2.4 安全性架构基线

```
请求层:
  ├── JWT 认证 (@PreAuthorize)
  ├── API Key 认证 (X-API-Key header, Hermes 使用)
  └── 角色权限矩阵 (ADMIN / ACADEMIC / TEACHER / STUDENT)

业务层:
  ├── assertOwner() - 资源所有权校验
  ├── verifyAccess() - 身份叠加校验 (多角色取并集)
  └── SecurityUtil - 统一安全上下文

数据层:
  ├── 参数化查询 (MyBatis-Plus LambdaQueryWrapper)
  ├── Flyway 版本化迁移
  └── 禁止直连 DB 做数据修复
```

---

## 三、里程碑与阶段性交付节点

### 3.1 Q3 路线图

```
7月 (当前)         8月                 9月
├─ v1.22.x ────────┤                    │
│  Round 1+2 修复   │                    │
│  ┌ PR #38 课件重构│                    │
│  └ PR #15 拆分    │                    │
│                   ├─ v1.23.0 ─────────┤
│                   │  Round 2-3 审计    │
│                   │  剩余 ~120 项修复   │
│                   │  staging 自动化     │
│                   │                    ├─ v1.24.0 ────
│                   │                    │  Round 3 审计
│                   │                    │  E2E 测试恢复
│                   │                    │  管理端 6 模块
│                   │                    │  Quiz 安全加固
```

### 3.2 详细里程碑

| 里程碑 | 版本 | 目标日期 | 关键交付物 | 验收标准 |
|--------|------|----------|-----------|----------|
| M1 | v1.22.1 | **7-22** | PR #38 课件架构重构合并 | 601 tests 全绿 + staging 验证 |
| M2 | v1.22.1 | **7-23** | 路径 C 事故复盘完成 + staging V202 dry-run | dry-run 脏数据评估 ≤ 100 行 |
| M3 | v1.23.0-rc1 | **7-25** | PR #15 拆分 v1.23.0-rc1 (~120 项) | 不与 v1.22.0+ 冲突 + 601 tests |
| M4 | v1.23.0 | **7-30** | Round 2-3 审计 + v1.23.0-rc1 合入 | staging 灰度 24h 无回滚 |
| M5 | v1.24.0-rc1 | **8-15** | 管理端 6 模块审计 | P0/P1-C 修复全覆盖 |
| M6 | v1.24.0 | **8-30** | E2E 测试恢复 + staging 自动化 + Quiz 加固 | E2E 通过率 > 95% |
| M7 | v1.25.0 | **9-15** | Round 3 完整审计 | 全模块 P0 清零 |

### 3.3 本周 (7-21 → 7-25) 冲刺目标

| 日期 | 事项 | 责任人 |
|------|------|--------|
| 7-21 | ✅ PR #32/#33 合并 + 技术战略发布 | AI |
| 7-22 | PR #38 合并 + staging V202 dry-run | owner + AI |
| 7-23 | v1.23.0-rc1 拆分 PR 创建 | owner |
| 7-24 | PR #31/#39 文档 PR rebase + 合并 | AI |
| 7-25 | 周度复盘 + 下周计划 | owner + AI |

---

## 四、资源分配与责任边界

### 4.1 团队组成

| 角色 | 人员 | 核心职责 | 时间分配 |
|------|------|---------|----------|
| 项目总负责人 | ailyedu2030 | 产品方向决策、功能开发、生产发布 | 60% |
| 总工程师 (AI) | Claude | 架构设计、代码审查、质量管控、技术审计 | 100% |
| Reviewer (待招募) | TBD | PR 审查、安全审计 | 10% (1-2h/w) |

### 4.2 责任边界矩阵 (RACI)

| 领域 | 总负责人 | 总工程师 | Reviewer |
|------|---------|---------|----------|
| 产品需求定义 | **A** | C | I |
| 功能开发 | **R** | C | I |
| 架构设计 | C | **A/R** | I |
| 代码审查 | C | **R** | **R** |
| 安全审计 | I | **R** | **R** |
| 测试策略 | I | **A/R** | C |
| 生产发布 | **A/R** | C | I |
| 事故响应 | **A** | **R** | I |
| 技术文档 | C | **A/R** | C |

> R=执行者, A=批准者, C=咨询者, I=知情者

### 4.3 进度管控机制

```
日维度:
  17:00 AI 自动 check PR 状态 (MERGEABLE/CONFLICTING)
  → 若 CONFLICTING → 立即 rebase
  → 若 PR #38 有新 commit → 评估是否需要暂缓其他 rebase

周维度:
  周五 16:00 周度复盘 (模板: docs/weekly-retro-template.md)
  → D1-D8 决策执行状态
  → 新发现风险
  → 下周冲刺计划

月维度:
  月末月度复盘
  → 里程碑达成率
  → Q3 路线图调整
```

### 4.4 卡点升级路径

```
识别卡点 → 评估级别:
  L1 (30 分内可解): AI 自动处理
  L2 (2 小时内): AI 处理 + 通知 owner
  L3 (需 owner 决策): 立即报告 owner + 附 3 个选项
  L4 (P0 事故): 触发应急响应 (见第七节)
```

---

## 五、代码评审与技术审计标准

### 5.1 PR 分级审批 (已在 AGENTS.md 实施)

| 等级 | 审批要求 | AI 行为 |
|------|---------|---------|
| P0 | 2 人 approve | 禁止 self-approve，等 reviewer |
| P1-C | 1 人 approve | 优先等 reviewer，24h 无人审 → AI 可 approve |
| P1-I/P2 | 24h 评论期 | 无异议 → self-approve |

### 5.2 代码审查检查清单

每次审查必须覆盖以下维度：

**安全性 (P0)**
- [ ] 所有读/写端点是否有 ownership 验证？
- [ ] 是否有未过滤的用户输入直接拼入 SQL？
- [ ] API Key 鉴权路径是否验证 resource ownership？
- [ ] 响应是否包含敏感字段（密码、token、内部 ID）？

**数据完整性 (P0)**
- [ ] DB schema 变更是否有对应的 Flyway migration？
- [ ] migration 是否有对应的 rollback SQL？
- [ ] 新字段是否有合理的默认值和 NOT NULL 约束？

**业务逻辑 (P1-C)**
- [ ] 状态机转换是否完整（无非法状态跳转）？
- [ ] 多角色场景是否考虑身份叠加（教师也是学生）？
- [ ] 边界条件：空列表、null 值、并发冲突？

**性能 (P2)**
- [ ] 是否有 N+1 查询？
- [ ] 新查询是否使用了索引？
- [ ] 是否有不必要的全表扫描？

### 5.3 技术审计周期

| 审计类型 | 频率 | 范围 | 执行人 |
|---------|------|------|--------|
| 增量审计 | 每次 PR | diff 范围 | AI |
| Round 审计 | 每 2 周 | 1 个模块的完整 E2E | AI |
| 安全审计 | 每月 | 全量 OWASP Top 10 | AI + Reviewer |
| 架构审计 | 每季度 | 全量架构决策 | AI + owner |

### 5.4 审计报告模板

每轮审计输出 `docs/audit/round-N-report.md`，包含：
1. 审计范围（模块/文件清单）
2. 发现缺陷（按 P0/P1-C/P1-I/P2 分级）
3. 根因分析
4. 修复 PR 引用
5. 回归测试覆盖

---

## 六、全流程质量管控体系

### 6.1 测试金字塔

```
         ┌──────┐
         │ E2E  │  Playwright · 3 条核心流程 (待恢复)
         ├──────┤
         │ 集成  │  Spring Boot Test · 601 用例
         ├──────┤
         │ 单元  │  JUnit 5 · Service/Util 全覆盖
         └──────┘
```

### 6.2 测试标准

#### 单元测试
- **覆盖率目标**: Service 层 > 80%，Util 层 > 90%
- **必须覆盖**: 正常路径 / 边界条件 / 异常路径
- **命名规范**: `methodName_Scenario_ExpectedBehavior`
- **框架**: JUnit 5 + Mockito

#### 集成测试
- **覆盖率目标**: Controller > 70%
- **数据库**: H2 内存数据库 (test profile)
- **安全测试必须覆盖**:
  - 无认证访问 → 401
  - 错误角色访问 → 403
  - 跨用户资源访问 (IDOR) → 403
  - 正常访问 → 200

#### E2E 测试 (当前禁用，M6 恢复)
- **场景**: 登录 → 选课 → 学习 → 测验 → 结业
- **框架**: Playwright
- **环境**: staging

#### 安全测试 (每次 PR)
- IDOR 扫描：所有 `/{id}` 端点交叉验证
- SQL 注入：特殊字符输入测试
- XSS：HTML/JS 注入测试

### 6.3 CI 质量门禁

```
PR 创建
  ↓
┌─ backend (mvn test) ────────────────┐
│  601 tests · 必须全绿                │
│  │                                   │
├─ frontend (npm run build) ──────────┤
│  TypeScript 编译 + Vite build       │
│  │                                   │
├─ docker (docker build) ─────────────┤
│  Dockerfile 可构建                   │
│  │                                   │
├─ trivy (安全扫描) ──────────────────┤
│  CRITICAL/HIGH → 阻塞               │
│  │                                   │
└─ e2e (当前禁用, M6 恢复) ────────────┘
  ↓
全部通过 → 允许 merge
```

### 6.4 发布前验证清单

- [ ] `bash scripts/local-dev-deploy.sh` 输出 `✅ 16 通过`
- [ ] CI 5/5 success
- [ ] staging 环境灰度验证 24h 无异常
- [ ] ROLLBACK_PLAN.md 覆盖当前版本
- [ ] `bash scripts/deploy-gate.sh check` 通过
- [ ] 生产 DB 备份已完成
- [ ] release note 已发布

---

## 七、风险预警与应急响应机制

### 7.1 风险登记表 (实时更新)

| ID | 风险 | 等级 | 概率 | 影响 | 缓解措施 | 触发条件 | 负责人 |
|----|------|------|------|------|----------|----------|--------|
| R-001 | PR #15 覆盖 v1.22.0 修复 | P0 | 高 | 高 | 拆分 v1.23.0-rc1 | 直接合入 | owner |
| R-002 | 生产未 staging 验证 | P0 | 中 | 极高 | 强制 staging 门禁 | 跳过 staging | AI |
| R-003 | owner 单点工作流 | P1 | 高 | 高 | reviewer 招募 | 持续 > 1 月 | owner |
| R-004 | PR #38 范围蔓延 > 125 files | P1 | 中 | 中 | 7-25 强制拆分 | 7-25 未完成 | AI |
| R-005 | V202 schema 脏数据 > 100 行 | P1 | 低 | 中 | dry-run 预评估 | staging 评估 | AI |
| R-006 | DB 迁移失败无回滚 | P1 | 低 | 高 | pg_dump 备份 | 迁移前 | AI |
| R-007 | CI 不稳定 (flaky tests) | P2 | 低 | 低 | 重试机制 | 连续 2 次失败 | AI |
| R-008 | 依赖供应链攻击 | P2 | 极低 | 极高 | Trivy 扫描 + dependabot batch | Trivy CRITICAL | AI |

### 7.2 应急响应级别

| 级别 | 定义 | 响应时间 | 通知范围 | 处理流程 |
|------|------|----------|----------|----------|
| **L1** | 生产中断（API 不可用、白屏） | 5 分钟 | 全团队 | 立即回滚 → 写事故复盘 (24h) |
| **L2** | 功能降级（部分功能不可用） | 30 分钟 | owner + AI | 评估影响 → 修复或回滚 |
| **L3** | 非关键异常（性能下降、UI 问题） | 2 小时 | AI 内部 | 记录 → 下个 patch 修复 |

### 7.3 P0 事故处理 SOP

```
1. 检测 (0 min)
   └─ 监控报警 / 用户反馈 / CI 失败

2. 止损 (0-5 min)
   └─ 立即回滚到上一个已知稳定版本
   └─ 禁用受影响功能 (feature flag)
   └─ 通知 owner

3. 根因分析 (5-60 min)
   └─ 查日志 / DB / commit history
   └─ 确定根因（不得猜测）
   └─ 写 incident report 草稿

4. 修复 (60 min - 24h)
   └─ 在 staging 环境重现
   └─ 修复 + 测试验证
   └─ staging 灰度 → 生产

5. 复盘 (24h 内)
   └─ 完整 incident report → docs/incidents/
   └─ 周度复盘会上讨论
   └─ 更新 ROLLBACK_PLAN
```

### 7.4 已知事故记录

| 日期 | 事故 | 级别 | 根因 | 状态 |
|------|------|------|------|------|
| 2026-07-01 | DatePickerYM 生产调试 | L1 | ssh 到生产改代码 | ✅ 已复盘 |
| 2026-07-17 | PR #30 self-merge 绕过 | L2 | enforce_admins 未启用 | ✅ 已复盘 |
| 2026-07-19 | v1.22.1 jar 部署绕过 | L1 | 部署流程未走 staging | 🔄 待复盘 |
| 2026-07-21 | PR #40 路径 C 合并 | L3 | 0 reviewer | 📝 已记录 |

---

## 八、Stakeholder 沟通与流程优化

### 8.1 沟通矩阵

| 受众 | 频率 | 内容 | 渠道 |
|------|------|------|------|
| 项目总负责人 | 每日 (17:00) | PR 状态、卡点、决策请求 | Trae 对话 |
| 总负责人 | 每周五 16:00 | 周度复盘 (D1-D8) | 周度复盘文档 |
| 总负责人 | 每决策节点 | 决策文件 + 验收 | docs/decisions/ |
| 外部 Reviewer (未来) | 每 PR | PR review request | GitHub |
| 社区 (未来) | 每月 | 技术博客 / Release note | GitHub Discussions |

### 8.2 流程优化迭代机制

```
每次事故/卡点 →
  1. 写复盘 (What / Why / How to prevent)
  2. 更新 AGENTS.md 行为约束
  3. 1 周试运行新规则
  4. 周度复盘评估新规则效果
  5. 效果达标 → 固化到 SKILL.md
     效果不达标 → 回退 + 重设计
```

### 8.3 已实施的流程改进

| 改进 | 触发 | 实施时间 | 效果 |
|------|------|----------|------|
| AGENTS.md Step 5.1 (owner PR 处理) | PR #30 self-merge 事故 | 7-20 | 路径 C 限制 + 分级审批 |
| dependabot batch 模式 | 16 个 PR 积压 | 7-18 | 每周 1-2 个汇总 PR |
| PR 分级审批规则 | 0 reviewer 阻塞 | 7-21 | P0/P1-C/P1-I 三级自审 |
| 路径 C incident report | 多次路径 C 使用 | 7-21 | 每次降级保护都留痕 |

---

## 九、技术资产沉淀与最佳实践

### 9.1 已沉淀技术资产

| 资产 | 路径 | 用途 |
|------|------|------|
| AGENTS.md | 项目根 | AI 行为宪章 + 开发入口 |
| PRODUCTION_SAFETY.md | docs/ | 生产安全铁律 |
| SKILL.md (4 层) | .claude/skills/ | 编码规范 (宪法/后端/前端/安全) |
| DECISION-2026-07-20.md | docs/decisions/ | 8 项重大决策记录 |
| ROLLBACK_PLAN.md | 项目根 | 版本回滚步骤 |
| weekly-retro-template.md | docs/ | 周度复盘模板 |
| staging-v202-dry-run.sh | scripts/ | staging 迁移预演脚本 |
| dependabot.yml | .github/ | 依赖管理 batch 模式 |
| CI workflow | .github/workflows/ci.yml | 5 任务 CI 流水线 |
| 事故复盘 (4 篇) | docs/incidents/ | 历史事故与教训 |
| 发布管理规范 | docs/发布管理.md | 发布门禁 + 决策流程 |
| 开发流程-完整版 | docs/开发流程-完整版.md | Step 1-6 完整规范 |

### 9.2 代码层面最佳实践

```yaml
禁止项:
  - lombok (注解处理器引入隐式依赖)
  - @Autowired 字段注入 (不可测试)
  - 构造器注入以外的依赖注入方式
  - 在生产 DB 写操作 (必须先 ask user)
  - 跳过 staging 直接发布
  - 5 分钟内重启/重建同一容器 > 1 次

推荐项:
  - 构造器注入 + private final 字段
  - LambdaQueryWrapper (类型安全, 防 SQL 注入)
  - Flyway 版本化 DB 迁移 (每条 migration 有回滚 SQL)
  - assertOwner() 模式 (统一资源所有权校验)
  - SecurityUtil 统一安全上下文
  - 异常全局处理 (BusinessException + ErrorCode 枚举)
  - 变更前备份 + commit message 写明 rollback 步骤
```

### 9.3 持续改进路线图

```
Q3 重点:
  ✓ AGENTS.md 行为约束体系
  ✓ PR 分级审批规则
  ✓ 技术战略文档
  □ Round 2-3 审计完成
  □ E2E 测试恢复
  □ staging 自动化部署
  □ reviewer 招募到位

Q4 展望:
  □ 性能基准测试体系
  □ 全量 OWASP 安全审计
  □ 微服务拆分评估 (基于业务增长)
  □ 技术博客 + 开源最佳实践分享
```

---

## 附录 A: 当前 PR 状态 (2026-07-21)

| PR | 标题 | 等级 | 状态 | 下一步 |
|----|------|------|------|--------|
| #41 | monitoring stack + pg_stat_statements | P1-I | 🆕 | 审查 |
| #39 | dependabot batch + reviewer 招募 | P1-I | CONFLICTING | rebase → 合并 |
| #38 | 课件架构重构 (125 files) | P1-C | 🔄 owner 开发中 | 7-22 合并 |
| #36 | 协作改进方案 | P1-I | CONFLICTING | rebase → 合并 |
| #35 | PR #15 冲突风险评估 | P1-I | CONFLICTING | rebase → 合并 |
| #34 | reviewer 请求包 | P1-I | CONFLICTING | rebase → 合并 |
| #31 | PR #30 事故复盘 + ROLLBACK_PLAN | P1-I | CONFLICTING | rebase → 合并 |
| #28 | 音频上传功能 | P1-C | CONFLICTING | rebase → 合并 |
| #22 | CI E2E fix | P2 | CONFLICTING | rebase → 合并 |
| #15 | 165 项审计修复 | P0 | CONFLICTING | 拆分 v1.23.0-rc1 |

## 附录 B: 决策记录索引

| 编号 | 决策 | 文件 |
|------|------|------|
| D1-D8 | 8 项项目决策 | `docs/decisions/DECISION-2026-07-20.md` |
| D2 详细 | PR #15 拆分计划 | `docs/decisions/v1.23.0-rc1-split-plan.md` |
| D3 模板 | ADR-002 课件重构 | `docs/decisions/ADR-002-template.md` |
| D5 实施 | AGENTS.md 分级审批 | `AGENTS.md` § PR 分级审批规则 |

---

**文档版本**: 1.0
**下次审查**: 2026-08-01 (月度复盘)
**状态**: ✅ 生效中
