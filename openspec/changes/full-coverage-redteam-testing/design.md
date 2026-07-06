# Design: 全量回归测试与缺陷闭环验证 (技术设计)

> **OpenSpec Change**: `full-coverage-redteam-testing`
> **Schema**: spec-driven
> **依赖**: proposal.md + specs/{full-coverage-regression,defect-closure-verification,linkage-defect-recheck,enrollment-withdraw-audit,environment-repair,permission-matrix,business-logic-spec,api-contract}/spec.md

---

## Context

**当前状态**:
- 项目版本: V1.20.0 (2026-07-04)
- 最近审计: 2026-07-06 165 项细粒度业务逻辑审计 + P0-P3 全量修复闭环 (commit `9ba513e`)
- 既有产出: 12 Agent 报告 15,791 行 + 5 大件交付物 (本任务不参考,老板明确要求"从头开始")
- 后端: 62 Controller / 396 endpoints / Spring Boot 3.2.12 + Java 17 + MyBatis-Plus
- 前端: 127 页面 / Vue 3.4 + Element Plus 2.5
- 未提交改动: 3 个文件 (UserList.vue / SecurityConfig.java / StorageApplicationController.java)
- 环境残缺: simdutf 库缺失(已修) / Redis 容器 requirepass 空密码(待修)

**约束**:
- 后端必须遵守 `.claude/skills/microcourse/SKILL.md` 的 25 条禁止项
- 数据库变更必须 Flyway 迁移
- 状态机变更必须更新 `docs/状态机设计.md`
- 不允许 Lombok,必须手写 getter/setter + 构造器注入
- 分层: Controller → Service → Repository,不允许 Controller 直调 Mapper
- 权限: JWT + @PreAuthorize (hasRole/hasAnyRole)
- 不动学校服务器 100.74.122.13 (生产铁律)
- 不动生产域名 microcourse.ailyedu.cn (118.89.201.33)

**干系人**:
- 测试执行: 阿福 (AI Agent, commander profile, MiniMax-M3)
- 协调/决策: 老板 (jackie)
- 上下文: 微信 (当前通道) + Hermes 跨会话记忆

---

## Goals / Non-Goals

### Goals (本设计要达成的)

1. **覆盖率 100%**: 后端 396 endpoint + 前端 127 页面全量覆盖,不允许跳过
2. **修复率 100%**: 所有发现缺陷 MUST 立即修复 + 跑通 + commit,不允许"待修"
3. **联动验证**: 6 个高危联动缺陷 (LD-002/005/006/009/011/015) 纵向串联复测
4. **盲区修补**: 退课链路 4 个操作单元 (ENR-005/006/008/009) 补审
5. **环境闭环**: simdutf / Redis 容器 / 其他工具链残缺 100% 修复
6. **可追溯**: 每个修复 commit 含审计 ID + 根因分析,不允许"表面修复"
7. **不重复造轮**: 严格不参考 7-06 审计的具体修复方案,只参考其发现清单做回归验证

### Non-Goals (明确排除)

- **不动学校服务器** (100.74.122.13) - 等老板授权通道后单独开摊
- **不动生产数据库** - 只动本机 PG 5433 / Redis 6380 测试容器
- **不动生产域名** (microcourse.ailyedu.cn) - 不在 Tailscale 网络
- **不深度性能测试** - 聚焦业务逻辑 + 交互
- **不重写架构** - 只修缺陷,不做大规模重构
- **不更新 7-06 审计的 12 份 Agent 报告** - 那些已经归档,本任务产出独立的 deliverable

---

## Decisions

### 决策 1: 12 Agent 并行 + 沙箱分片

**选择**: 12 个独立子任务分片执行,每分片 ~26-27 个最小测试单元
**理由**:
- 严格符合 Vibe Coding SOP"单 Agent 不许包揽整链路"规则
- 沙箱隔离避免上下文互相污染
- 圆桌离散(round-robin)分配,确保单 Agent 跨 6+ 业务域
**备选**:
- ❌ 串行 12 Agent: 太慢,token 消耗反而更高(反复加载上下文)
- ❌ 4-6 Agent 粗分片: 违反 SOP"12 Agent 上限"硬性规则

### 决策 2: 4 维校验 + 业务基线对齐

**选择**: 每测试单元跑 4 维校验 (按钮异常 / 业务常识 / 交互错乱 / 功能残缺)
**基线来源**:
- `docs/数据字典.md` v0.5 (字段类型/长度/约束)
- `docs/API契约-Phase1.md` v1.2 (REST 路径/请求/响应/错误码)
- `docs/权限矩阵.md` v2.0 (REST 端点/角色权限)
- `docs/状态机设计.md` v1.0 (状态机/转换规则)
- `docs/开发规范.md` v1.4 (代码规范/分层)
**理由**:
- 项目宪法已规定 5 份真文档为"唯一真相"
- 测试用例 MUST 与文档契约对齐,文档没写的 = 缺失契约 = P1-I

### 决策 3: 修复策略 - 根因 + 回归双层

**选择**: 每缺陷修复 = 根因分析 + 单测验证 + 联动回归
**根因分析模板**:
```
## 根因
[为什么 BUG 产生 - 不是"代码写错了",而是"设计/假设/状态机错了"]

## 修复
[为什么这种修法是根因修法 - 不是"加 try-catch 吞异常"这种表面修]

## 回归测试
[至少 1 个跨链路测试证明修复有效 + 不破坏其他]
```
**理由**: AGENTS.md 明确"必须根因分析,禁止表面修复",这是 P0 复盘规则

### 决策 4: 数据库变更走 Flyway V167+ (如果发现需改 schema)

**选择**: 任何 DB 变更走 Flyway 迁移,V167+ 编号
**理由**: 25 条禁止项之一"手动建数据库 migration 不更新 docs/数据字典.md"
**规则**:
- 每次建表/改字段 MUST 同时更新 `docs/数据字典.md` v0.6
- 迁移命名: `V167__add_withdraw_audit_log.sql` (kebab-case + 描述)
- 必须在 docker 启动后跑 `mvn flyway:migrate` 验证通过

### 决策 5: 状态机变更同步 docs/状态机设计.md

**选择**: 任一业务对象(订单/选课/审批/课程)状态机变更 MUST 同步更新文档
**理由**: 25 条禁止项之一"状态机变更必须更新 docs/状态机设计.md"
**状态机清单 (已知)**:
- 订单: PENDING → PAID → REFUNDED / CANCELLED
- 选课: ACTIVE → COMPLETED / WITHDRAWN
- 课程: DRAFT → PENDING_REVIEW → APPROVED → PUBLISHED / REJECTED
- 审批: PENDING → APPROVED / REJECTED
- 微专业申报: DRAFT → SUBMITTED → UNDER_REVIEW → APPROVED / REJECTED
**新增** (本任务可能发现):
- 退课: ACTIVE → WITHDRAWING → WITHDRAWN / FAILED

### 决策 6: 不参考 7-06 审计的具体修复方案

**选择**: 严格不参考 7-06 12 份 Agent 报告 + 5 大件的具体修复代码
**理由**: 老板明确"我不要你去验收别人的成果,从头开始"
**允许**:
- ✅ 参考 7-06 审计的"发现了哪些 OP 单元有缺陷"清单(用于补审未覆盖 OP)
- ✅ 参考 7-06 审计的"5 个 P0 联动缺陷"清单(用于联动复测入口)
**禁止**:
- ❌ 抄 7-06 报告的修复方案
- ❌ 看 7-06 Agent 报告里的代码 diff
- ❌ 复用 7-06 整改表里的"待修"状态(本任务必须直接修)

---

## Risks / Trade-offs

| 风险 | 等级 | 缓解 |
|---|---|---|
| **修改引入新 BUG** | 中 | 修复后跑联动回归;每个 commit 单独原子化 |
| **环境不可重现** | 低 | 先修 simdutf / Redis 容器,3 项冒烟全绿再开始 |
| **测试覆盖不全** | 中 | 严格 396 endpoint + 127 页面清单化,跑前清单化 + 跑后核对 |
| **Redis 密码改了连不上后端** | 中 | 改 docker-compose 同时改 application.yml / .env,统一管理 |
| **学校服务器盲点** | 高 | 本任务产出明确标"未覆盖:学校服务器",等老板授权后单独开摊 |
| **12 Agent 沙箱分片 token 爆炸** | 中 | 每个 Agent 独立 context 限制 5MB,跑完即销毁 |
| **修复与未提交改动冲突** | 中 | 优先 commit 现有 3 个未提交改动,再开始测试修复 |
| **DB 变更影响现有数据** | 高 | 测试用 5433 端口的 test 容器,与生产 DB 完全隔离 |
| **修复没跑通** | 低 | 每个修复 MUST 跑至少 1 个测试通过,跑不通视为修复失败重做 |
| **老板看不到进度** | 低 | 每个阶段完成立刻汇报,不闷头跑 |

### Trade-off 1: 覆盖率 vs 速度

**取舍**: 100% 覆盖率 > 速度
**理由**: 老板"项目等成功是第一原则,用户体验是至高原则",不允许为了快漏测

### Trade-off 2: 修复深度 vs 范围

**取舍**: 根因修复 + 联动回归 > 表面修复
**理由**: 25 条禁止项 + 7-06 复盘报告 P1-P5 教训,表面修 = 必然返工

### Trade-off 3: 独立发现 vs 复用 7-06 报告

**取舍**: 独立发现(不抄修复方案) > 复用
**理由**: 老板明确要求,避免"被锚定偏见带偏"

---

## Migration Plan

### 阶段 0: 环境修复 (0.5-1h,本汇报已完成大半)

- ✅ simdutf symlink (sudo 加 33→34 链接)
- ⏳ Redis 容器 requirepass 修复
- ⏳ 3 项冒烟测试全绿

### 阶段 1: OpenSpec 规划 (1-2h,本汇报完成 proposal+specs)

- ✅ proposal.md (Why/What/Capabilities/Impact)
- ✅ 8 份 specs/*.md (5 New + 3 Modified)
- ✅ OpenSpec validate pass
- ⏳ design.md (本文档)
- ⏳ tasks.md (任务拆解)

### 阶段 2: 12 Agent 并行单元化红队测试 (核心,~8-12h)

- 先 commit 3 个未提交改动 (UserList.vue / SecurityConfig.java / StorageApplicationController.java)
- 12 个独立子任务分片
- 每个分片 ~26-27 个最小测试单元 (单页面+单按钮+单分支)
- 4 维校验 (按钮异常/业务常识/交互错乱/功能残缺)
- 每发现缺陷 = 立即修复 + 跑通 + commit
- 产出: 12 份 Agent 报告 + 165 项验证记录 + 修复 commits

### 阶段 3: 双层交叉红队复测 (~2-4h)

- 横向: 同链路多按钮对比 (LD-002/005/006/009/011/015)
- 纵向: 全流程串联跑通
- 产出: 31 个联动缺陷复测报告

### 阶段 4: 5 大件交付物 (~2-3h)

- 5 份交付物 (按 SOP 阶段 4 规范)
- 缺陷总整改表 (按 4 大类分区)
- 最终 commit + 全部回归测试通过

### 阶段 5: OpenSpec archive (15min)

- 跑 `openspec archive full-coverage-redteam-testing`
- 产出归档到 `openspec/changes/archive/`
- 同步更新 `openspec/specs/` (如有新能力)
- 关闭本 change

### 回滚策略 (如果任一阶段失败)

- 阶段 2/3 失败: 回滚该 Agent 报告对应的 commits
- 阶段 4 失败: 5 大件回退到 0 版本
- 阶段 5 失败: archive 失败不影响代码,直接 git revert
- 任何时候: `git log --oneline -20` 找最后稳定 commit 回滚

---

## Open Questions (待解决)

1. **Q1**: 学校服务器 (100.74.122.13) 什么时候给通道?
   - **默认**: 本任务不动,等老板给信号
2. **Q2**: 测试数据用什么角色? 复用什么账号? 创建新账号?
   - **默认**: 复用 admin/teacher/student 演示账号,需要时再创建 `_test_` 后缀测试账号
3. **Q3**: Flyway 迁移从 V167 开始,还是要清理 V166 之前的?
   - **默认**: V167 开始,不动历史迁移
4. **Q4**: 修复后的 commits 要 squash 还是保持原子?
   - **默认**: 保持原子,便于追溯,merge 时由老板 squash
5. **Q5**: 12 Agent 是真·独立子进程还是 12 个沙箱分片?
   - **默认**: 12 个沙箱分片 (沙箱隔离 + 独立 context),不真起子进程
6. **Q6**: 7-06 报告里说"已修"的项,要不要重做单元测试?
   - **默认**: 必须重做,验证修复有效,这是"缺陷闭环验证" spec 的核心要求
