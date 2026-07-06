# 课程管理域 · Spec 漂移全量修复 (Course Domain Drift Full Fix)

> **OpenSpec Change**: `course-domain-drift-fix`
> **Schema**: spec-driven
> **创建日期**: 2026-07-07
> **关联扫描报告**: `memories/scratchpad/course-domain-spec-drift.md`
> **关联根因分析**: `memories/scratchpad/course-domain-rca.md`

---

## Why

**问题陈述**: 课程管理域（占平台 ~30% 业务面）通过 6 个并列扫描任务发现 **127 项 spec 漂移**:
- 4 项 P0 状态机守卫缺失（含业务安全绕过漏洞）
- 9 项 P1-C 业务错误（含 V1 硬编码业务错位 + 8 项权限越权）
- 105 项 P1-I 文档漂移（其中 85 项是 API 契约整域缺失）
- 9 项 P2 优化项

**根因模式分析**（详见 RCA 报告）: 这 127 项并非孤立缺陷，而是 3 个系统性根因的表现：

| 根因模式 | 覆盖项数 | 占比 | 含 P0 | 含 P1-C |
|---------|---------|------|:-----:|:-------:|
| **P1: Spec-Code 双向熵增** | 107 | 84% | 0 | 4 |
| **P2: 状态机守卫碎片化** | 7 | 6% | **4** | 0 |
| **P3: 权限与校验层杂交** | 13 | 10% | 0 | **5** |
| **合计** | **127** | **100%** | **4** | **9** |

**为什么必须现在修**:
1. P0 必修: 4 项 P0 中 3 项是业务绕过漏洞（S1 空课程提交、S3/S4 审核绕过），严重破坏核心业务闭环
2. V1 P1-C: `checkReviewTimeout` @Scheduled 任务因硬编码 status=5 永远检测不到待审核课程，下架课程却收到催办通知
3. 文档零覆盖: 课程管理域 85 个端点在 `docs/API契约-Phase1.md` 中**0% 记录**（Phase 1 有意识地排除了课程域但从未规划 Phase 2 补齐）
4. 增量恶化: 任何新增端点/字段都会扩大漂移，不会自然收敛
5. 老板明确指令: "必须全量修复,不能留下任何技术债务,保证用户体验"

**为什么不能逐项修补**: 127 项逐项修补是治标, 同样的根因会产生新的同类缺陷。本次修复采用"根因模式修复" + "残余项修补"双轨制。

---

## What Changes

### 新增能力

- **CAP-1 状态机统一入口**: 新建 `CourseStateMachine` bean,作为所有课程状态变更的单一入口(含 `canTransitionTo` + 业务守卫 hook + 乐观锁)
- **CAP-2 自动化 API 契约**: 集成 SpringDoc OpenAPI, 自动生成 `docs/api/openapi.yaml`, 覆盖 85 个课程管理端点
- **CAP-3 自动化数据字典**: 新建 `db-schema-doc-generator` 脚本, 从 Flyway migration SQL 逆向生成数据字典 Markdown
- **CAP-4 权限矩阵可执行化**: 新建 `EndpointPermissionTest`, 自动比较 `@PreAuthorize` vs 权限矩阵 v4.0, CI 门禁
- **CAP-5 静态扫描增强**: 扩展 `precheck.sh` 增加 3 条规则: 枚举魔数扫描 / Controller 业务逻辑扫描 / SPEC-CODE 差异扫描

### 修改能力

- **CAP-6 (修改) CourseController**: 重构 `updateStatus()` 拒绝 status=1/4 (PENDING_REVIEW/PUBLISHED) 必须用专用端点
- **CAP-7 (修改) CourseAuditService**: `approve()/reject()/publish()` 改用 `canTransitionTo` 而非硬编码 WHERE
- **CAP-8 (修改) 数据字典 v0.5→v0.6**: 修正 14 项漂移 (索引/CHECK/已删约束/类型偏差)
- **CAP-9 (修改) API 契约 v1.2→v1.3**: 创建 `docs/API契约-课程管理.md` 覆盖 85 端点
- **CAP-10 (修改) 权限矩阵 v2.0→v4.0**: 修复 11 项漂移 + 补充 30 端点 (分类/课时/课件/定价/批量/状态/复制/封面)
- **CAP-11 (修改) 状态机设计 v1.0→v1.1**: 补全 3 项守卫 (章节内容/驳回长度/历史前提), 记录"通用端点拒绝 PUBLISHED"约定
- **CAP-12 (修改) 开发规范 v1.4→v1.5**: 新增 5 条禁止项 (魔数/Controller业务逻辑/Owner下沉/Spec门禁/Contract-first)

### 新增 Capabilities (OpenSpec specs)

- `course-domain-state-machine`: 统一状态机入口 + 守卫 hook 契约
- `course-domain-permission-test`: 权限矩阵可执行化测试
- `course-domain-contract-auto-gen`: OpenAPI 自动生成与 CI 门禁

---

## Capabilities

### New Capabilities
- `course-domain-state-machine`: 统一状态机入口
- `course-domain-permission-test`: 权限矩阵可执行化
- `course-domain-contract-auto-gen`: API 契约自动化生成

### Modified Capabilities
- `data-contract`: 数据字典 v0.5→v0.6
- `api-contract`: API 契约 v1.2→v1.3 (新增课程管理域)
- `permission-matrix`: 权限矩阵 v2.0→v4.0
- `business-logic`: 状态机设计 v1.0→v1.1
- `dev-standards`: 开发规范 v1.4→v1.5

---

## Impact

### 跨域影响 (符合"跨 3 域以上"标注规则)

| 域 | 影响范围 |
|----|---------|
| **后端 Java 域** | CourseController (3 处改) + CourseAuditService (4 处改) + CourseService (1 处改 V1) + 11 章节/视频/分类/标签/套件/课时 Controller (受影响) + ErrorCode.java (新增/调整) |
| **前端 Vue 域** | 4 个课程管理页面 + 8 个 API 模块 (新端点 /api/courses/teacher/{teacherId} 等需新增对应 API 调用) |
| **数据库域** | 0 新增迁移, 但修复 5 项 V153 CHECK 约束/索引登记 |
| **中间件域** | Redis key 不变, 但新增课程状态机缓存键约定 |
| **测试域** | 新增 7 个测试类 (StateMachineTest, PermissionTest, ContractTest, MagicNumberTest, ControllerLintTest, RegressionTest, RCATest) + 100+ TC |
| **工具链域** | 新增 3 个脚本 (openapi-gen, db-schema-doc-gen, controller-lint) |
| **CI/CD 域** | 扩展 precheck.sh + 新增 3 个 GitHub Actions |

### 数据影响

- ✅ **本机测试库**: 可读可写 (用于回归测试)
- ✅ **本机测试数据**: 可造测试课程/章节/视频 (避开生产 PII)
- ❌ **生产数据库**: 不动
- ❌ **生产服务器**: 不动

### 风险

| 风险 | 等级 | 缓解 |
|------|:----:|------|
| 状态机重构影响线上流程 | 高 | 编写详尽穷举测试 (新 ExhaustiveStateMachineTest), 所有 7 态×7态 = 49 转换全覆盖 |
| OpenAPI 自动生成与现有 DTO 不兼容 | 中 | 渐进式迁移, 先标记 `@Operation` 注解, 不强制改变 DTO 结构 |
| 数据字典反向生成与手动编辑冲突 | 中 | 生成结果作为参考, 由总工程师审批合并 |
| 权限矩阵 CI 门禁误报 | 中 | 矩阵 v4.0 第一版允许特定豁免白名单 |
| TC 表 225+ 测试用例执行时间长 | 低 | 拆 5 个并行 Agent, 每个 Agent 50+ TC |
| 引入新 Bug | 中 | 每个修复后跑穷举回归, 不增量累积 |
| 影响其他域 (EnrollmentService 等依赖课程状态) | 高 | 集成测试覆盖跨域影响 |

---

## 执行策略（基于 RCA 模式修复）

### 阶段 1: P0 必修 + V1 (5 项必修, 立即阻塞)

**目标**: 修复 4 项 P0 + 1 项 P1-C (V1)
**方法**: 状态机守卫补全 + 硬编码替换

| # | 任务 | RCA 模式 | 阻塞性 |
|---|------|---------|--------|
| 1 | CourseAuditServiceImpl.submitForReview() 补"章节下至少一个视频/练习"校验 | 模式 2 | 阻塞课程审核 |
| 2 | RejectRequest DTO 加 @Size(min=10) | 模式 2 | 阻塞驳回流程 |
| 3 | publish() 增加"此前 PUBLISHED"历史校验 (使用 lastPublishedAt 字段) | 模式 2 | 阻塞安全 |
| 4 | updateStatus() 拒绝 status=1/4, 必须用专用端点 | 模式 2 | 阻塞安全绕过 |
| 5 | CourseServiceImpl.java:237 硬编码 status=5 改为 CourseStatus.PENDING_REVIEW.getCode() | 模式 3 | 阻塞 @Scheduled 任务 |

**前置**: 读 CourseAuditServiceImpl.java:100-200 行确认守卫位置, 读 CourseStatus.java 确认枚举
**后置**: 跑 ExhaustiveStateMachineTest 验证 49 个转换, 跑 CourseServiceImpl 相关测试

### 阶段 2: 状态机统一入口重构 (模式 2 系统性修复)

**目标**: 一次性消除模式 2 的全部 7 项 + 防止新增类似漂移
**方法**: 引入 CourseStateMachine bean

| # | 任务 | 包含 |
|---|------|------|
| 6 | 新建 `service/CourseStateMachine.java` 接口 | 唯一状态变更入口 |
| 7 | 实现 `CourseStateMachineImpl`, 封装 canTransitionTo + 乐观锁 + 业务守卫 hook | — |
| 8 | 重构 `CourseAuditServiceImpl.approve()/reject()/publish()` 使用 CourseStateMachine | S5/S6/S7 |
| 9 | 重构 `CourseAdminServiceImpl.updateStatus()` 拒绝 status=1/4, 其他状态委托给 CourseStateMachine | S4 |
| 10 | 写 `CourseStateMachineExhaustiveTest`, 覆盖 7×7=49 个状态转换 | — |

### 阶段 3: 权限与校验修复 (模式 3 系统性修复)

**目标**: 修复 5 项 P1-C 权限越权 + 6 项规范违反 + V1/V7 魔数
**方法**: `@PreAuthorize` 修正 + 静态扫描 + Controller 瘦身

| # | 任务 | 包含 |
|---|------|------|
| 11 | submit 端点移除 ADMIN, 改为 hasRole('TEACHER') | 权限矩阵 #1 |
| 12 | 收藏端点路径对齐 + 权限限制为 STUDENT | 权限矩阵 #2/#3 |
| 13 | 确认或实现缺失端点: GET /api/courses/teacher/{teacherId} | 权限矩阵 #4 |
| 14 | 确认或实现缺失端点: POST /api/videos/{id}/retry | 权限矩阵 #5 |
| 15 | 确认或实现缺失端点: GET /api/videos/{id}/analytics | 权限矩阵 #6 |
| 16 | 确认或实现缺失端点: POST /api/videos/batch-upload | 权限矩阵 #7 |
| 17 | POST /api/courses/{id}/reviews 移除 ADMIN | 权限矩阵 #8 |
| 18 | DELETE /reviews/{reviewId} 改为仅 ADMIN | 权限矩阵 #9 |
| 19 | CourseController.updateCover() 业务逻辑下沉到 CourseCoverService | V2 |
| 20 | CourseController.getCourseStudents() 角色判断下沉到 Service | V3 |
| 21 | VideoController 4 处业务逻辑下沉 (uploadCover + reportVideoProgress + asInt) | V4/V5/V6 |
| 22 | CourseServiceImpl.java:158 硬编码 status==4 改为 CourseStatus.PUBLISHED.getCode() | V7 |
| 23 | R.java 删除 timestamp 字段 (与契约对齐) | V8 |
| 24 | VideoController.reportVideoProgress 改用 DTO 而非 Map | V9 |

### 阶段 4: API 契约自动化 (模式 1 系统性修复)

**目标**: 一次性消除 85 项 API 契约漂移 + 防止新增
**方法**: SpringDoc OpenAPI 集成

| # | 任务 | 包含 |
|---|------|------|
| 25 | pom.xml 集成 springdoc-openapi-starter-webmvc-ui | — |
| 26 | 给 85 个课程管理端点添加 @Operation/@Parameter/@ApiResponse 注解 | — |
| 27 | 写 `OpenApiGenerationTest`, 验证启动时生成 OpenAPI 规范 | — |
| 28 | 创建 `docs/api/openapi.yaml` (自动生成, 检入 git) | 85 端点 |
| 29 | 写 `ContractEndpointCoverageTest`, 断言所有 Controller 都有 OpenAPI 注解 | — |
| 30 | CI 门禁: 增量 diff 端点但未更新 openapi.yaml → fail | — |

### 阶段 5: 数据字典反向生成 (模式 1 子项)

**目标**: 消除 14 项数据字典漂移 + 防止新增
**方法**: 从 Flyway SQL 自动生成

| # | 任务 | 包含 |
|---|------|------|
| 31 | 写 `scripts/db-schema-doc-gen.sh`, 解析 V*__*.sql 提取表/字段/索引/约束 | — |
| 32 | 跑脚本生成 `docs/data-dictionary.generated.md`, 与手写 md 对比 | — |
| 33 | 修正手写 md 中的 14 项漂移 (JSONB→TEXT, 已删约束移除, 等) | D1-D14 |
| 34 | CI 门禁: 生成结果与手写 md diff 不一致 → fail | — |

### 阶段 6: 权限矩阵可执行化 (模式 3 子项)

**目标**: 修复 11 项权限漂移 + 防止新增
**方法**: EndpointPermissionTest

| # | 任务 | 包含 |
|---|------|------|
| 35 | 创建 `docs/权限矩阵.md` v4.0 (修复 11 项 + 补充 30 端点) | — |
| 36 | 写 `EndpointPermissionTest`, 解析权限矩阵 v4.0 为预期表 | — |
| 37 | 反射扫描所有 `@RestController.@PreAuthorize` 为实际表 | — |
| 38 | 断言两张表一致, 不一致 → fail | — |
| 39 | CI 集成: `bash scripts/check-permission.sh` | — |

### 阶段 7: 静态扫描增强 (模式 3 子项)

**目标**: 自动捕获"治标"修复 (如加注释而非根治), 让违规无处遁形
**方法**: 扩展 precheck.sh

| # | 任务 | 包含 |
|---|------|------|
| 40 | `precheck.sh` 增加规则: `grep -rn "\.eq(.*Status,\s*\d" src/main/ && exit 1` | V1/V7 防御 |
| 41 | `precheck.sh` 增加规则: 扫描 `@RestController` 文件含 `SecurityUtil.hasRole` → warn | V3 防御 |
| 42 | `precheck.sh` 增加规则: 扫描 `@RestController` 文件含 `MultipartFile` 魔数判断 → warn | V2/V4 防御 |
| 43 | `controller-lint.sh` 新脚本: 完整扫描 Controller 层违反 | — |

### 阶段 8: 文档同步 (模式 1 子项)

**目标**: 同步 5 份 spec 文档到 v0.6 / v1.3 / v4.0 / v1.1 / v1.5
**方法**: 基于阶段 1-7 修复, 同步更新文档

| # | 任务 | 包含 |
|---|------|------|
| 44 | `docs/数据字典.md` v0.5→v0.6 (修复 14 项) | D1-D14 |
| 45 | `docs/API契约-课程管理.md` 新建 (覆盖 85 端点) | C1-C85 |
| 46 | `docs/API契约-Phase1.md` 加引用章节指向课程管理文档 | — |
| 47 | `docs/权限矩阵.md` v2.0→v4.0 (修复 11 项 + 补充 30 端点) | 权限矩阵 #1-11 |
| 48 | `docs/状态机设计.md` v1.0→v1.1 (补 3 项守卫 + 通用端点拒绝约定) | S1-S4 |
| 49 | `docs/开发规范.md` v1.4→v1.5 (新增 5 条禁止项) | V2-V10 |

### 阶段 9: 测试设计与执行 (基于对齐后的 spec)

**目标**: 设计 225+ TC, 全部 PASS 后才算完成
**方法**: 逐域打穿, 单 Agent 上下文累积

| # | 任务 | 包含 |
|---|------|------|
| 50 | 写 `memories/scratchpad/course-test-units.md` 设计 225+ TC | TC-001 ~ TC-225 |
| 51 | 执行 TC-001 ~ TC-050 (课程 CRUD + 状态机) | — |
| 52 | 执行 TC-051 ~ TC-100 (章节 4 类型 + 视频) | — |
| 53 | 执行 TC-101 ~ TC-150 (定价 + 审批 + 批量) | — |
| 54 | 执行 TC-151 ~ TC-200 (分类 + 标签 + 套件 + 课时 + 课件) | — |
| 55 | 执行 TC-201 ~ TC-225 (评价 + 复习 + 跨域) | — |
| 56 | 全部 PASS 后 commit, 进入下一域 (用户管理) | — |

---

## 前置条件 (OpenSpec rules 强制要求)

1. ✅ 已读 OpenSpec `AGENTS.md` + 4 个 skill
2. ✅ 已读项目宪法 `.claude/skills/microcourse/SKILL.md` + 6 份 references
3. ✅ 已读课程管理域扫描报告 (`memories/scratchpad/course-domain-spec-drift.md`)
4. ✅ 已读根因分析 (`memories/scratchpad/course-domain-rca.md`)
5. ✅ 已读 `docs/开发规范.md` v1.4 (现状)
6. ✅ 已读 5 份 spec 文档 (现状)
7. ✅ 已修复 OpenSpec CLI (simdutf)

## 引用

- 关联 OpenSpec change: `full-coverage-redteam-testing` (上一轮, 55/56 完成, 含课程管理粗粒度扫描)
- 扫描报告: `memories/scratchpad/course-domain-spec-drift.md` (127 项漂移)
- 根因分析: `memories/scratchpad/course-domain-rca.md` (3 个系统性根因)

## 反向依赖 (本 change 完成后会更新)

- 5 份 spec 文档版本号 +1
- `microcourse-backend` skill 增加"状态机统一入口"模式说明
- `microcourse-frontend` skill 增加"OpenAPI 自动生成"模式说明
- `production-safety` skill 不变 (本任务不动生产)

---

**总任务数**: 56
**估计工时**: 8-10 天 (单人 Agent 串行; 含修复 + 文档 + 225+ TC 执行)
**优先级**: 老板指令"必须全量修复,不能留下任何技术债务", 无可延期